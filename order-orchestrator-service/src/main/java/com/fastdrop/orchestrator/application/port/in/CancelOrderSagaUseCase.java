package com.fastdrop.orchestrator.application.port.in;

import com.fastdrop.orchestrator.application.port.in.command.CancelOrderSagaCommand;
import com.fastdrop.orchestrator.domain.model.OrderSaga;

public interface CancelOrderSagaUseCase {
    OrderSaga cancel(CancelOrderSagaCommand command);
}
