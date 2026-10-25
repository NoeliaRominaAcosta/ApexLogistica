package com.fastdrop.orchestrator.application.port.in.command;

public record CancelOrderSagaCommand(
        String orderId,
        String reason
) {
}
