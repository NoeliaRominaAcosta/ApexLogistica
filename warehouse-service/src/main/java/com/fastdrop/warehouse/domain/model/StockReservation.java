package com.fastdrop.warehouse.domain.model;

import com.fastdrop.warehouse.domain.event.ReservationConfirmedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReleasedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReservationRejectedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReservedDomainEvent;
import com.fastdrop.warehouse.domain.event.WarehouseDomainEvent;
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
 * Agregado puro de dominio para una reserva de stock.
 * Las mutaciones de negocio son invocadas por las actions de la máquina de estados en memoria.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {

    private UUID id;
    private String orderId;
    private String warehouseId;
    private String sku;
    private int quantity;
    private String deliveryAddress;
    private String trackingNumber;
    private String failureReason;
    private ReservationState state;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private final List<WarehouseDomainEvent> domainEvents = new ArrayList<>();

    public static StockReservation createNew(String orderId, String warehouseId, String sku, int quantity, String deliveryAddress) {
        Instant now = Instant.now();
        return StockReservation.builder()
                .orderId(orderId)
                .warehouseId(warehouseId != null && !warehouseId.isBlank() ? warehouseId : "WH-CENTRAL-01")
                .sku(sku)
                .quantity(quantity)
                .deliveryAddress(deliveryAddress)
                .state(ReservationState.NEW)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Crea una reserva directamente en estado CANCELLED (tombstone) cuando llega una compensación
     * antes que el comando de reserva original.
     */
    public static StockReservation createCancelledTombstone(String orderId, String reason) {
        Instant now = Instant.now();
        StockReservation reservation = StockReservation.builder()
                .orderId(orderId)
                .warehouseId("WH-CENTRAL-01")
                .sku("UNKNOWN")
                .quantity(0)
                .state(ReservationState.CANCELLED)
                .failureReason(reason)
                .createdAt(now)
                .updatedAt(now)
                .build();
        reservation.domainEvents.add(new StockReleasedDomainEvent(orderId, "WH-CENTRAL-01", "UNKNOWN", 0, reason));
        return reservation;
    }

    public void reserve(InventoryItem item) {
        item.reserve(this.quantity);
        this.warehouseId = item.getWarehouseId();
        this.updatedAt = Instant.now();
        this.domainEvents.add(new StockReservedDomainEvent(
                this.orderId, this.warehouseId, this.sku, this.quantity, this.deliveryAddress
        ));
    }

    public void reject(String reason) {
        this.failureReason = reason;
        this.updatedAt = Instant.now();
        this.domainEvents.add(new StockReservationRejectedDomainEvent(
                this.orderId, this.sku, this.quantity, reason
        ));
    }

    public void confirm(String trackingNumber) {
        this.trackingNumber = trackingNumber;
        this.updatedAt = Instant.now();
        this.domainEvents.add(new ReservationConfirmedDomainEvent(this.orderId, trackingNumber));
    }

    public void cancel(String reason, InventoryItem item) {
        if (item != null && this.quantity > 0) {
            item.release(this.quantity);
        }
        this.failureReason = reason;
        this.updatedAt = Instant.now();
        this.domainEvents.add(new StockReleasedDomainEvent(
                this.orderId, this.warehouseId, this.sku, this.quantity, reason
        ));
    }

    public void changeState(ReservationState newState) {
        this.state = newState;
        this.updatedAt = Instant.now();
    }

    /**
     * Alias de compatibilidad para serialización JSON existente (`status`).
     */
    public ReservationState getStatus() {
        return this.state;
    }

    public List<WarehouseDomainEvent> pullDomainEvents() {
        List<WarehouseDomainEvent> copy = List.copyOf(this.domainEvents);
        this.domainEvents.clear();
        return copy;
    }
}
