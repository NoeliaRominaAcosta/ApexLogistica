package com.fastdrop.warehouse.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.event.StockCancelledEvent;
import com.fastdrop.common.event.StockReservationRejectedEvent;
import com.fastdrop.common.event.StockReservedEvent;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.warehouse.application.port.out.DomainEventPublisherPort;
import com.fastdrop.warehouse.domain.event.ReservationConfirmedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReleasedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReservationRejectedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReservedDomainEvent;
import com.fastdrop.warehouse.domain.event.WarehouseDomainEvent;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.repository.SpringDataOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisherAdapter implements DomainEventPublisherPort {

    private final SpringDataOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(List<WarehouseDomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        String traceParent = TraceUtils.getOrCreateTraceParent();
        for (WarehouseDomainEvent domainEvent : events) {
            if (domainEvent instanceof StockReservedDomainEvent e) {
                StockReservedEvent contract = StockReservedEvent.builder()
                        .orderId(e.orderId())
                        .warehouseId(e.warehouseId())
                        .sku(e.sku())
                        .quantity(e.quantity())
                        .deliveryAddress(e.deliveryAddress())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("StockReservation", e.orderId(), contract.getEventType(), contract, traceParent);
            } else if (domainEvent instanceof StockReservationRejectedDomainEvent e) {
                StockReservationRejectedEvent contract = StockReservationRejectedEvent.builder()
                        .orderId(e.orderId())
                        .sku(e.sku())
                        .quantity(e.quantity())
                        .reason(e.reason())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("StockReservation", e.orderId(), contract.getEventType(), contract, traceParent);
            } else if (domainEvent instanceof StockReleasedDomainEvent e) {
                StockCancelledEvent contract = StockCancelledEvent.builder()
                        .orderId(e.orderId())
                        .warehouseId(e.warehouseId())
                        .sku(e.sku())
                        .quantity(e.quantity())
                        .reason(e.reason())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("StockReservation", e.orderId(), contract.getEventType(), contract, traceParent);
            } else if (domainEvent instanceof ReservationConfirmedDomainEvent e) {
                log.debug("Reserva confirmada internamente para orden {}, tracking={}", e.orderId(), e.trackingNumber());
            }
        }
    }

    private void saveToOutbox(String aggregateType, String aggregateId, String eventType, Object payload, String traceParent) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxEventJpaEntity outboxEvent = OutboxEventJpaEntity.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(jsonPayload)
                    .traceContext(traceParent)
                    .createdAt(Instant.now())
                    .build();
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Error al serializar payload de outbox para {}", aggregateId, e);
            throw new RuntimeException("Fallo al serializar evento outbox", e);
        }
    }
}
