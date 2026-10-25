package com.fastdrop.warehouse.application.port.in.command;

public record ConfirmReservationCommand(
        String orderId,
        String trackingNumber
) {
}
