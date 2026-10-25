package com.fastdrop.warehouse.domain.event;

import java.time.Instant;

public record StockReservedDomainEvent(
        String orderId,
        String warehouseId,
        String sku,
        int quantity,
        String deliveryAddress,
        Instant occurredAt
) implements WarehouseDomainEvent {

    public StockReservedDomainEvent(String orderId, String warehouseId, String sku, int quantity, String deliveryAddress) {
        this(orderId, warehouseId, sku, quantity, deliveryAddress, Instant.now());
    }
}
