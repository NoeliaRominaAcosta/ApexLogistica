package com.fastdrop.dispatch.outbox;

import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.dispatch.domain.OutboxEvent;
import com.fastdrop.dispatch.repository.OutboxEventRepository;
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

    @Value("${app.kafka.topics.dispatch-events:logistics.dispatch-events}")
    private String dispatchTopic;

    @Scheduled(fixedDelayString = "${app.outbox.polling-rate-ms:1000}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents;
        try {
            pendingEvents = outboxEventRepository.findPendingEventsForProcessing(50);
        } catch (Exception e) {
            pendingEvents = outboxEventRepository.findTop50ByProcessedAtIsNullOrderByCreatedAtAsc();
        }

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Procesando {} eventos de Outbox en Dispatch Service", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                if (event.getTraceContext() != null) {
                    TraceUtils.setTraceContext(event.getTraceContext());
                }

                ProducerRecord<String, String> record = new ProducerRecord<>(
                        dispatchTopic,
                        event.getAggregateId(),
                        event.getPayload()
                );

                if (event.getTraceContext() != null) {
                    record.headers().add(new RecordHeader(TraceUtils.TRACEPARENT_HEADER,
                            event.getTraceContext().getBytes(StandardCharsets.UTF_8)));
                }
                record.headers().add(new RecordHeader("eventType",
                        event.getEventType().getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Fallo al publicar evento Outbox ID={} a Kafka", event.getId(), ex);
                    }
                });

                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Evento Outbox {} [{}] publicado exitosamente a tópico {}",
                        event.getId(), event.getEventType(), dispatchTopic);
            } catch (Exception e) {
                log.error("Error al procesar evento Outbox en Dispatch ID={}", event.getId(), e);
            } finally {
                TraceUtils.clearTraceContext();
            }
        }
    }
}
