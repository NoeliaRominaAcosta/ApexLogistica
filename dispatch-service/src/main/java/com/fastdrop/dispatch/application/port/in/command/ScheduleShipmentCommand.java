package com.fastdrop.dispatch.application.port.in.command;

public record ScheduleShipmentCommand(
        String orderId,
        String warehouseId,
        String deliveryAddress
) {
}
