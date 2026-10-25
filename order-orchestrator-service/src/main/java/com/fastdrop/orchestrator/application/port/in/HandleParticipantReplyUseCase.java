package com.fastdrop.orchestrator.application.port.in;

import com.fastdrop.orchestrator.application.port.in.command.ParticipantReplyCommand;
import com.fastdrop.orchestrator.domain.model.OrderSaga;

public interface HandleParticipantReplyUseCase {
    OrderSaga onParticipantReply(ParticipantReplyCommand command);
}
