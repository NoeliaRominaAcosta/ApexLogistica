package com.fastdrop.orchestrator.domain.model;

import com.fastdrop.orchestrator.domain.command.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agregado puro de dominio que representa la instancia de la Saga para una orden.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSaga {

    private UUID id;
    private String orderId;
    private String customerId;
    private String sku;
    private int quantity;
    private String warehouseId;
    private String deliveryAddress;
    private String shipmentId;
    private String trackingNumber;
    private String driverId;
    private String failureReason;
    private SagaState state;
    private Instant deadline;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private final List<SagaOutboundCommand> outboundCommands = new ArrayList<>();

    public static OrderSaga createNew(String orderId, String customerId, String sku, int quantity,
                                      String warehouseId, String deliveryAddress) {
        Instant now = Instant.now();
        return OrderSaga.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .customerId(customerId != null && !customerId.isBlank() ? customerId : "CUST-DEFAULT")
                .sku(sku)
                .quantity(quantity)
                .warehouseId(warehouseId != null && !warehouseId.isBlank() ? warehouseId : "WH-CENTRAL-01")
                .deliveryAddress(deliveryAddress)
                .state(SagaState.NEW)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void requestReserveStock() {
        this.outboundCommands.add(new ReserveStockSagaCommand(
                this.orderId, this.sku, this.quantity, this.warehouseId, this.deliveryAddress
        ));
    }

    public void onStockReserved(String assignedWarehouseId) {
        if (assignedWarehouseId != null && !assignedWarehouseId.isBlank()) {
            this.warehouseId = assignedWarehouseId;
        }
        this.outboundCommands.add(new ScheduleDeliverySagaCommand(
                this.orderId, this.warehouseId, this.sku, this.quantity, this.deliveryAddress
        ));
    }

    public void onStockRejected(String reason) {
        this.failureReason = reason;
    }

    public void onDeliveryScheduled(String shipmentId, String trackingNumber, String driverId) {
        this.shipmentId = shipmentId;
        this.trackingNumber = trackingNumber;
        this.driverId = driverId;
        this.outboundCommands.add(new ConfirmReservationSagaCommand(this.orderId, trackingNumber));
    }

    public void onShipmentPickedUp(String driverId) {
        if (driverId != null && !driverId.isBlank()) {
            this.driverId = driverId;
        }
    }

    public void onShipmentDelivered() {
        this.deadline = null;
    }

    public void requestCancelShipment(String reason) {
        this.failureReason = reason;
        this.outboundCommands.add(new CancelShipmentSagaCommand(this.orderId, reason));
    }

    public void requestReleaseStock(String reason) {
        if (reason != null && !reason.isBlank()) {
            this.failureReason = reason;
        }
        String effectiveReason = this.failureReason != null ? this.failureReason : "Compensación de Saga";
        this.outboundCommands.add(new ReleaseStockSagaCommand(this.orderId, effectiveReason));
    }

    public void changeState(SagaState newState, Duration stepTimeout) {
        this.state = newState;
        this.updatedAt = Instant.now();
        if (newState.requiresStepDeadline() && stepTimeout != null) {
            this.deadline = Instant.now().plus(stepTimeout);
        } else {
            this.deadline = null;
        }
    }

    public SagaState getStatus() {
        return this.state;
    }

    public List<SagaOutboundCommand> pullOutboundCommands() {
        List<SagaOutboundCommand> copy = List.copyOf(this.outboundCommands);
        this.outboundCommands.clear();
        return copy;
    }
}
