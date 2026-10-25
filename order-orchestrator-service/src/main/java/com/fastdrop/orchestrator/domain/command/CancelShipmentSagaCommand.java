package com.fastdrop.orchestrator.domain.command;

import java.time.Instant;

public record CancelShipmentSagaCommand(
        String orderId,
        String reason,
        Instant occurredAt
) implements SagaOutboundCommand {

    public CancelShipmentSagaCommand(String orderId, String reason) {
        this(orderId, reason, Instant.now());
    }
}
