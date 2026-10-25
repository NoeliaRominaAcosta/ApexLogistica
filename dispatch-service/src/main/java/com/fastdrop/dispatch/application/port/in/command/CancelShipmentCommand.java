package com.fastdrop.dispatch.application.port.in.command;

public record CancelShipmentCommand(
        String orderId,
        String reason
) {
}
