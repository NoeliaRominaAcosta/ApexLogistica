package com.fastdrop.orchestrator.application.port.out;

import com.fastdrop.orchestrator.domain.model.OrderSaga;
import com.fastdrop.orchestrator.domain.model.SagaState;
import com.fastdrop.statemachine.api.TransitionSubject;

public record SagaContext(OrderSaga saga) implements TransitionSubject<SagaState> {

    @Override
    public String machineId() {
        return saga.getOrderId();
    }

    @Override
    public SagaState currentState() {
        return saga.getState();
    }
}
