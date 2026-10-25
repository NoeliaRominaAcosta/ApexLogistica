package com.fastdrop.warehouse.domain.event;

import java.time.Instant;

public record StockReservationRejectedDomainEvent(
        String orderId,
        String sku,
        int quantity,
        String reason,
        Instant occurredAt
) implements WarehouseDomainEvent {

    public StockReservationRejectedDomainEvent(String orderId, String sku, int quantity, String reason) {
        this(orderId, sku, quantity, reason, Instant.now());
    }
}
