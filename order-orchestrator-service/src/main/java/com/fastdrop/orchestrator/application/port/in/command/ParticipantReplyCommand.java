package com.fastdrop.orchestrator.application.port.in.command;

import com.fastdrop.orchestrator.domain.model.SagaEvent;

import java.util.Map;

public record ParticipantReplyCommand(
        String eventId,
        String orderId,
        SagaEvent sagaEvent,
        Map<String, Object> metadata
) {
}
