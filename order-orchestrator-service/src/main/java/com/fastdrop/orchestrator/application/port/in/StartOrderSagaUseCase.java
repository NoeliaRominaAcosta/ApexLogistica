package com.fastdrop.orchestrator.application.port.in;

import com.fastdrop.orchestrator.application.port.in.command.StartOrderSagaCommand;
import com.fastdrop.orchestrator.domain.model.OrderSaga;

public interface StartOrderSagaUseCase {
    OrderSaga start(StartOrderSagaCommand command);
}
