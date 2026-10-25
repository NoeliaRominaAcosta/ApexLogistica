package com.fastdrop.orchestrator.infrastructure.adapter.out.statemachine;

import com.fastdrop.orchestrator.application.port.out.SagaContext;
import com.fastdrop.orchestrator.application.port.out.SagaStateMachinePort;
import com.fastdrop.orchestrator.domain.model.SagaEvent;
import com.fastdrop.orchestrator.domain.model.SagaState;
import com.fastdrop.statemachine.ssm.AbstractStateMachineAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;

@Component
public class SagaStateMachineAdapter
        extends AbstractStateMachineAdapter<SagaContext, SagaState, SagaEvent>
        implements SagaStateMachinePort {

    public SagaStateMachineAdapter(
            @Qualifier("sagaStateMachineFactory") StateMachineFactory<SagaState, SagaEvent> factory) {
        super(factory);
    }
}
