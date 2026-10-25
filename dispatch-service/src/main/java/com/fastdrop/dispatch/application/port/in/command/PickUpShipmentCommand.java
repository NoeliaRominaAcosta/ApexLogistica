package com.fastdrop.dispatch.application.port.in.command;

public record PickUpShipmentCommand(
        String orderId,
        String driverId
) {
}
