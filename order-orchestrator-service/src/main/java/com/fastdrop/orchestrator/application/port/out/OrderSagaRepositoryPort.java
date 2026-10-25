package com.fastdrop.orchestrator.application.port.out;

import com.fastdrop.orchestrator.domain.model.OrderSaga;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderSagaRepositoryPort {
    Optional<OrderSaga> findByOrderId(String orderId);
    List<OrderSaga> findExpiredSagas(Instant now, int limit);
    OrderSaga save(OrderSaga saga);
}
