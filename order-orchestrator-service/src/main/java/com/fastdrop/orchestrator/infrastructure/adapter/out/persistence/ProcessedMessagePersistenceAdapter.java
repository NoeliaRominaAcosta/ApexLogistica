package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence;

import com.fastdrop.orchestrator.application.port.out.ProcessedMessagePort;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.ProcessedMessageJpaEntity;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.repository.SpringDataProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ProcessedMessagePersistenceAdapter implements ProcessedMessagePort {

    private final SpringDataProcessedMessageRepository repository;

    @Override
    public boolean isAlreadyProcessed(String eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void markProcessed(String eventId, String orderId, String eventType) {
        repository.save(ProcessedMessageJpaEntity.builder()
                .eventId(eventId)
                .orderId(orderId)
                .eventType(eventType)
                .processedAt(Instant.now())
                .build());
    }
}
