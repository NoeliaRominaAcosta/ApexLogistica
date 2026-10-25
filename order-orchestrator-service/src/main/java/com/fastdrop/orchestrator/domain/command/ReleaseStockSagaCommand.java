package com.fastdrop.orchestrator.domain.command;

import java.time.Instant;

public record ReleaseStockSagaCommand(
        String orderId,
        String reason,
        Instant occurredAt
) implements SagaOutboundCommand {

    public ReleaseStockSagaCommand(String orderId, String reason) {
        this(orderId, reason, Instant.now());
    }
}
