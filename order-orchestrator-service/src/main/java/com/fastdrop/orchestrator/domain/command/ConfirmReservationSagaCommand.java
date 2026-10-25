package com.fastdrop.orchestrator.domain.command;

import java.time.Instant;

public record ConfirmReservationSagaCommand(
        String orderId,
        String trackingNumber,
        Instant occurredAt
) implements SagaOutboundCommand {

    public ConfirmReservationSagaCommand(String orderId, String trackingNumber) {
        this(orderId, trackingNumber, Instant.now());
    }
}
