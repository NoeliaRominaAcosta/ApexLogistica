package com.fastdrop.dispatch.domain.model;

import com.fastdrop.dispatch.domain.event.DeliveryFailedDomainEvent;
import com.fastdrop.dispatch.domain.event.DeliveryScheduledDomainEvent;
import com.fastdrop.dispatch.domain.event.DispatchDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentCancelledDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentDeliveredDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentPickedUpDomainEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agregado puro de dominio para un despacho (Shipment).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    private UUID id;
    private String orderId;
    private String trackingNumber;
    private String warehouseId;
    private String driverId;
    private String deliveryAddress;
    private ShipmentState state;
    private String estimatedDelivery;
    private String shippingLabelUrl;
    private String failureReason;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private final List<DispatchDomainEvent> domainEvents = new ArrayList<>();

    public static Shipment createPendingRouting(String orderId, String warehouseId, String deliveryAddress) {
        Instant now = Instant.now();
        return Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .warehouseId(warehouseId)
                .deliveryAddress(deliveryAddress)
                .trackingNumber("PENDING-" + orderId)
                .state(ShipmentState.PENDING_ROUTING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static Shipment createCancelledTombstone(String orderId, String reason) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .id(id)
                .orderId(orderId)
                .trackingNumber("CANCELLED-" + orderId)
                .state(ShipmentState.CANCELLED)
                .failureReason(reason)
                .createdAt(now)
                .updatedAt(now)
                .build();
        shipment.domainEvents.add(new ShipmentCancelledDomainEvent(id.toString(), orderId, reason));
        return shipment;
    }

    public void assignRoute(RouteAssignment assignment) {
        this.driverId = assignment.driverId();
        this.trackingNumber = assignment.trackingNumber();
        this.estimatedDelivery = assignment.estimatedDelivery();
        this.shippingLabelUrl = assignment.shippingLabelUrl();
        this.updatedAt = Instant.now();
        this.domainEvents.add(new DeliveryScheduledDomainEvent(
                this.id != null ? this.id.toString() : UUID.randomUUID().toString(),
                this.orderId,
                this.trackingNumber,
                this.driverId,
                this.estimatedDelivery,
                this.shippingLabelUrl
        ));
    }

    public void markFailed(String reason) {
        this.failureReason = reason;
        if (this.trackingNumber == null || this.trackingNumber.startsWith("PENDING-")) {
            this.trackingNumber = "UNASSIGNED-" + this.orderId;
        }
        this.updatedAt = Instant.now();
        this.domainEvents.add(new DeliveryFailedDomainEvent(this.orderId, reason));
    }

    public void pickUp(String driverId) {
        if (driverId != null && !driverId.isBlank()) {
            this.driverId = driverId;
        }
        this.updatedAt = Instant.now();
        this.domainEvents.add(new ShipmentPickedUpDomainEvent(
                this.id != null ? this.id.toString() : "",
                this.orderId,
                this.trackingNumber,
                this.driverId
        ));
    }

    public void deliver() {
        this.updatedAt = Instant.now();
        this.domainEvents.add(new ShipmentDeliveredDomainEvent(
                this.id != null ? this.id.toString() : "",
                this.orderId,
                this.trackingNumber,
                this.driverId
        ));
    }

    public void cancel(String reason) {
        this.failureReason = reason;
        this.updatedAt = Instant.now();
        this.domainEvents.add(new ShipmentCancelledDomainEvent(
                this.id != null ? this.id.toString() : "",
                this.orderId,
                reason
        ));
    }

    public void changeState(ShipmentState newState) {
        this.state = newState;
        this.updatedAt = Instant.now();
    }

    public ShipmentState getStatus() {
        return this.state;
    }

    public List<DispatchDomainEvent> pullDomainEvents() {
        List<DispatchDomainEvent> copy = List.copyOf(this.domainEvents);
        this.domainEvents.clear();
        return copy;
    }
}
