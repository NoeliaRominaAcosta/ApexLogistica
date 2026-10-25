package com.fastdrop.orchestrator.application.port.in;

import com.fastdrop.orchestrator.domain.model.OrderSaga;

import java.util.Optional;

public interface HandleSagaTimeoutUseCase {
    void handleExpiredSagas();
    Optional<OrderSaga> findByOrderId(String orderId);
}
