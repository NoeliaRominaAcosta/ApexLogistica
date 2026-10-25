package com.fastdrop.dispatch.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.event.DeliveryFailedEvent;
import com.fastdrop.common.event.DeliveryScheduledEvent;
import com.fastdrop.common.event.ShipmentCancelledEvent;
import com.fastdrop.common.event.ShipmentDeliveredEvent;
import com.fastdrop.common.event.ShipmentPickedUpEvent;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.dispatch.application.port.out.DomainEventPublisherPort;
import com.fastdrop.dispatch.domain.event.DeliveryFailedDomainEvent;
import com.fastdrop.dispatch.domain.event.DeliveryScheduledDomainEvent;
import com.fastdrop.dispatch.domain.event.DispatchDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentCancelledDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentDeliveredDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentPickedUpDomainEvent;
import com.fastdrop.dispatch.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import com.fastdrop.dispatch.infrastructure.adapter.out.persistence.repository.SpringDataOutboxRepository;
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
    public void publish(List<DispatchDomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        String traceParent = TraceUtils.getOrCreateTraceParent();
        for (DispatchDomainEvent domainEvent : events) {
            if (domainEvent instanceof DeliveryScheduledDomainEvent e) {
                DeliveryScheduledEvent contract = DeliveryScheduledEvent.builder()
                        .shipmentId(e.shipmentId())
                        .orderId(e.orderId())
                        .trackingNumber(e.trackingNumber())
                        .driverId(e.driverId())
                        .estimatedDeliveryTime(e.estimatedDeliveryTime())
                        .shippingLabelUrl(e.shippingLabelUrl())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("Shipment", e.orderId(), contract.getEventType(), contract, traceParent);
            } else if (domainEvent instanceof DeliveryFailedDomainEvent e) {
                DeliveryFailedEvent contract = DeliveryFailedEvent.builder()
                        .orderId(e.orderId())
                        .reason(e.reason())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("Shipment", e.orderId(), contract.getEventType(), contract, traceParent);
            } else if (domainEvent instanceof ShipmentPickedUpDomainEvent e) {
                ShipmentPickedUpEvent contract = ShipmentPickedUpEvent.builder()
                        .shipmentId(e.shipmentId())
                        .orderId(e.orderId())
                        .trackingNumber(e.trackingNumber())
                        .driverId(e.driverId())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("Shipment", e.orderId(), contract.getEventType(), contract, traceParent);
            } else if (domainEvent instanceof ShipmentDeliveredDomainEvent e) {
                ShipmentDeliveredEvent contract = ShipmentDeliveredEvent.builder()
                        .shipmentId(e.shipmentId())
                        .orderId(e.orderId())
                        .trackingNumber(e.trackingNumber())
                        .driverId(e.driverId())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("Shipment", e.orderId(), contract.getEventType(), contract, traceParent);
            } else if (domainEvent instanceof ShipmentCancelledDomainEvent e) {
                ShipmentCancelledEvent contract = ShipmentCancelledEvent.builder()
                        .shipmentId(e.shipmentId())
                        .orderId(e.orderId())
                        .reason(e.reason())
                        .occurredAt(e.occurredAt())
                        .traceParent(traceParent)
                        .build();
                saveToOutbox("Shipment", e.orderId(), contract.getEventType(), contract, traceParent);
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
            log.error("Error serializando payload Outbox en Dispatch para {}", aggregateId, e);
            throw new RuntimeException("Fallo al serializar evento outbox en Dispatch", e);
        }
    }
}
