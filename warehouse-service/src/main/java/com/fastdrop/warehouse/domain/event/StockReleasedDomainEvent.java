package com.fastdrop.warehouse.domain.event;

import java.time.Instant;

public record StockReleasedDomainEvent(
        String orderId,
        String warehouseId,
        String sku,
        int quantity,
        String reason,
        Instant occurredAt
) implements WarehouseDomainEvent {

    public StockReleasedDomainEvent(String orderId, String warehouseId, String sku, int quantity, String reason) {
        this(orderId, warehouseId, sku, quantity, reason, Instant.now());
    }
}
