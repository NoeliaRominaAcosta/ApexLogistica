package com.fastdrop.warehouse.application.port.in.command;

public record CancelReservationCommand(
        String orderId,
        String reason
) {
}
