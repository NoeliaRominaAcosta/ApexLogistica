package com.fastdrop.orchestrator.domain.command;

import java.time.Instant;

public record ReserveStockSagaCommand(
        String orderId,
        String sku,
        int quantity,
        String warehouseId,
        String deliveryAddress,
        Instant occurredAt
) implements SagaOutboundCommand {

    public ReserveStockSagaCommand(String orderId, String sku, int quantity, String warehouseId, String deliveryAddress) {
        this(orderId, sku, quantity, warehouseId, deliveryAddress, Instant.now());
    }
}
