package com.fastdrop.orchestrator.domain.command;

import java.time.Instant;

public record ScheduleDeliverySagaCommand(
        String orderId,
        String warehouseId,
        String sku,
        int quantity,
        String deliveryAddress,
        Instant occurredAt
) implements SagaOutboundCommand {

    public ScheduleDeliverySagaCommand(String orderId, String warehouseId, String sku, int quantity, String deliveryAddress) {
        this(orderId, warehouseId, sku, quantity, deliveryAddress, Instant.now());
    }
}
