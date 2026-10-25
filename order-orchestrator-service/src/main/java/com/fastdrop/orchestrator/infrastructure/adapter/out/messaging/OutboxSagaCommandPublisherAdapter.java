package com.fastdrop.orchestrator.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.event.CancelShipmentCommand;
import com.fastdrop.common.event.ConfirmReservationCommand;
import com.fastdrop.common.event.ReleaseStockCommand;
import com.fastdrop.common.event.ReserveStockCommand;
import com.fastdrop.common.event.ScheduleDeliveryCommand;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.orchestrator.application.port.out.SagaCommandPublisherPort;
import com.fastdrop.orchestrator.domain.command.*;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.repository.SpringDataOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxSagaCommandPublisherAdapter implements SagaCommandPublisherPort {

    private final SpringDataOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.warehouse-commands:warehouse.commands}")
    private String warehouseCommandsTopic;

    @Value("${app.kafka.topics.dispatch-commands:dispatch.commands}")
    private String dispatchCommandsTopic;

    @Override
    public void publish(List<SagaOutboundCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        String traceParent = TraceUtils.getOrCreateTraceParent();
        for (SagaOutboundCommand cmd : commands) {
            if (cmd instanceof ReserveStockSagaCommand c) {
                ReserveStockCommand contract = ReserveStockCommand.builder()
                        .orderId(c.orderId())
                        .sku(c.sku())
                        .quantity(c.quantity())
                        .warehouseId(c.warehouseId())
                        .deliveryAddress(c.deliveryAddress())
                        .occurredAt(c.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox(c.orderId(), contract.getEventType(), warehouseCommandsTopic, contract, traceParent);
            } else if (cmd instanceof ConfirmReservationSagaCommand c) {
                ConfirmReservationCommand contract = ConfirmReservationCommand.builder()
                        .orderId(c.orderId())
                        .trackingNumber(c.trackingNumber())
                        .occurredAt(c.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox(c.orderId(), contract.getEventType(), warehouseCommandsTopic, contract, traceParent);
            } else if (cmd instanceof ReleaseStockSagaCommand c) {
                ReleaseStockCommand contract = ReleaseStockCommand.builder()
                        .orderId(c.orderId())
                        .reason(c.reason())
                        .occurredAt(c.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox(c.orderId(), contract.getEventType(), warehouseCommandsTopic, contract, traceParent);
            } else if (cmd instanceof ScheduleDeliverySagaCommand c) {
                ScheduleDeliveryCommand contract = ScheduleDeliveryCommand.builder()
                        .orderId(c.orderId())
                        .warehouseId(c.warehouseId())
                        .sku(c.sku())
                        .quantity(c.quantity())
                        .deliveryAddress(c.deliveryAddress())
                        .occurredAt(c.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox(c.orderId(), contract.getEventType(), dispatchCommandsTopic, contract, traceParent);
            } else if (cmd instanceof CancelShipmentSagaCommand c) {
                CancelShipmentCommand contract = CancelShipmentCommand.builder()
                        .orderId(c.orderId())
                        .reason(c.reason())
                        .occurredAt(c.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox(c.orderId(), contract.getEventType(), dispatchCommandsTopic, contract, traceParent);
            }
        }
    }

    private void saveToOutbox(String orderId, String eventType, String destinationTopic, Object payload, String traceParent) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxEventJpaEntity entity = OutboxEventJpaEntity.builder()
                    .aggregateType("OrderSaga")
                    .aggregateId(orderId)
                    .eventType(eventType)
                    .destinationTopic(destinationTopic)
                    .payload(jsonPayload)
                    .traceContext(traceParent)
                    .createdAt(Instant.now())
                    .build();
            outboxRepository.save(entity);
        } catch (JsonProcessingException e) {
            log.error("Error serializando comando de saga en Outbox para orden {}", orderId, e);
            throw new RuntimeException("Error serializando comando de saga", e);
        }
    }
}
