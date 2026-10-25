package com.fastdrop.orchestrator.infrastructure.adapter.in.scheduler;

import com.fastdrop.orchestrator.application.port.in.HandleSagaTimeoutUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaTimeoutScheduler {

    private final HandleSagaTimeoutUseCase handleSagaTimeoutUseCase;

    @Scheduled(fixedDelayString = "${app.saga.timeout-polling-rate-ms:10000}")
    public void pollExpiredSagas() {
        try {
            handleSagaTimeoutUseCase.handleExpiredSagas();
        } catch (Exception e) {
            log.error("Error procesando timeouts de OrderSaga", e);
        }
    }
}
