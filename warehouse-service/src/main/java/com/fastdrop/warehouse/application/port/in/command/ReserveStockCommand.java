package com.fastdrop.warehouse.application.port.in.command;

public record ReserveStockCommand(
        String orderId,
        String sku,
        int quantity,
        String warehouseId,
        String deliveryAddress
) {
}
