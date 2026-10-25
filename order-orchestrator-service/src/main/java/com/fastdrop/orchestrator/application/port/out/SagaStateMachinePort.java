package com.fastdrop.orchestrator.application.port.out;

import com.fastdrop.orchestrator.domain.model.SagaEvent;
import com.fastdrop.orchestrator.domain.model.SagaState;
import com.fastdrop.statemachine.api.StateMachinePort;

public interface SagaStateMachinePort
        extends StateMachinePort<SagaContext, SagaState, SagaEvent> {
}
