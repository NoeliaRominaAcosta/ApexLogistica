package com.fastdrop.warehouse.outbox;

import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.warehouse.domain.OutboxEvent;
import com.fastdrop.warehouse.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.inventory-events:warehouse.inventory-events}")
    private String inventoryTopic;

    @Scheduled(fixedDelayString = "${app.outbox.polling-rate-ms:1000}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents;
        try {
            pendingEvents = outboxEventRepository.findPendingEventsForProcessing(50);
        } catch (Exception e) {
            // Fallback para ambientes/bases donde SKIP LOCKED no esté disponible
            pendingEvents = outboxEventRepository.findTop50ByProcessedAtIsNullOrderByCreatedAtAsc();
        }

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Procesando {} eventos de Outbox en Warehouse Service", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Configurar contexto de trazabilidad
                if (event.getTraceContext() != null) {
                    TraceUtils.setTraceContext(event.getTraceContext());
                }

                ProducerRecord<String, String> record = new ProducerRecord<>(
                        inventoryTopic,
                        event.getAggregateId(), // Partition key garantiza orden por entidad
                        event.getPayload()
                );

                // Inyectar traceparent y event_type en Kafka headers
                if (event.getTraceContext() != null) {
                    record.headers().add(new RecordHeader(TraceUtils.TRACEPARENT_HEADER,
                            event.getTraceContext().getBytes(StandardCharsets.UTF_8)));
                }
                record.headers().add(new RecordHeader("eventType",
                        event.getEventType().getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Fallo al publicar evento Outbox ID={} a Kafka", event.getId(), ex);
                    }
                });

                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Evento Outbox {} [{}] publicado exitosamente a tópico {}",
                        event.getId(), event.getEventType(), inventoryTopic);
            } catch (Exception e) {
                log.error("Error al procesar evento Outbox ID={}", event.getId(), e);
            } finally {
                TraceUtils.clearTraceContext();
            }
        }
    }
}
