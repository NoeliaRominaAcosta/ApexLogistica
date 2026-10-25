package com.fastdrop.dispatch.infrastructure.adapter.in.scheduler;

import com.fastdrop.dispatch.application.port.in.ScheduleShipmentUseCase;
import com.fastdrop.dispatch.application.port.out.ShipmentRepositoryPort;
import com.fastdrop.dispatch.domain.model.Shipment;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingRoutingRecoveryScheduler {

    private final ShipmentRepositoryPort shipmentRepository;
    private final ScheduleShipmentUseCase scheduleShipmentUseCase;

    @Value("${app.recovery.pending-routing-threshold-seconds:30}")
    private long thresholdSeconds;

    @Scheduled(fixedDelayString = "${app.recovery.polling-rate-ms:15000}")
    public void recoverPendingRoutings() {
        Instant threshold = Instant.now().minusSeconds(thresholdSeconds);
        List<Shipment> stuckShipments = shipmentRepository.findByStateAndCreatedAtBefore(
                ShipmentState.PENDING_ROUTING, threshold
        );
        for (Shipment stuck : stuckShipments) {
            try {
                log.warn("Reintentando cálculo de ruta para envío pendiente: orderId={}", stuck.getOrderId());
                scheduleShipmentUseCase.retryPendingRouting(stuck.getOrderId());
            } catch (Exception e) {
                log.error("Error al recuperar envío pendiente orderId={}", stuck.getOrderId(), e);
            }
        }
    }
}
