package com.fastdrop.orchestrator.application.port.in.command;

public record StartOrderSagaCommand(
        String orderId,
        String customerId,
        String sku,
        int quantity,
        String warehouseId,
        String deliveryAddress
) {
}
