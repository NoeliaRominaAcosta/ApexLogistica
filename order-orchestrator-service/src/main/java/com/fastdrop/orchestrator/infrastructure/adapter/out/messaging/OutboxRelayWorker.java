package com.fastdrop.orchestrator.infrastructure.adapter.out.messaging;

import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.repository.SpringDataOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
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
public class OutboxRelayWorker {

    private final SpringDataOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.polling-rate-ms:1000}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventJpaEntity> pendingEvents;
        try {
            pendingEvents = outboxRepository.findPendingEventsForProcessing(50);
        } catch (Exception e) {
            pendingEvents = outboxRepository.findTop50ByProcessedAtIsNullOrderByCreatedAtAsc();
        }

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEventJpaEntity event : pendingEvents) {
            try {
                if (event.getTraceContext() != null) {
                    TraceUtils.setTraceContext(event.getTraceContext());
                }

                ProducerRecord<String, String> record = new ProducerRecord<>(
                        event.getDestinationTopic(),
                        event.getAggregateId(),
                        event.getPayload()
                );

                if (event.getTraceContext() != null) {
                    record.headers().add(new RecordHeader(TraceUtils.TRACEPARENT_HEADER,
                            event.getTraceContext().getBytes(StandardCharsets.UTF_8)));
                }
                record.headers().add(new RecordHeader("eventType",
                        event.getEventType().getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Fallo al publicar comando Outbox ID={} a Kafka", event.getId(), ex);
                    }
                });

                event.setProcessedAt(Instant.now());
                outboxRepository.save(event);
                log.info("Comando Outbox {} [{}] publicado exitosamente a tópico {}",
                        event.getId(), event.getEventType(), event.getDestinationTopic());
            } catch (Exception e) {
                log.error("Error procesando comando Outbox ID={}", event.getId(), e);
            } finally {
                TraceUtils.clearTraceContext();
            }
        }
    }
}
