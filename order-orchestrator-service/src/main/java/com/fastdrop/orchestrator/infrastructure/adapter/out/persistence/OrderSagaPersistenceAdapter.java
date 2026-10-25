package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence;

import com.fastdrop.orchestrator.application.port.out.OrderSagaRepositoryPort;
import com.fastdrop.orchestrator.domain.model.OrderSaga;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.OrderSagaJpaEntity;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.mapper.OrderSagaPersistenceMapper;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.repository.SpringDataOrderSagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderSagaPersistenceAdapter implements OrderSagaRepositoryPort {

    private final SpringDataOrderSagaRepository repository;
    private final OrderSagaPersistenceMapper mapper;

    @Override
    public Optional<OrderSaga> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public List<OrderSaga> findExpiredSagas(Instant now, int limit) {
        List<OrderSagaJpaEntity> entities;
        try {
            entities = repository.findExpiredSagasForUpdate(now, limit);
        } catch (Exception e) {
            entities = repository.findTop50ByDeadlineBeforeOrderByDeadlineAsc(now);
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public OrderSaga save(OrderSaga saga) {
        return mapper.toDomain(repository.save(mapper.toEntity(saga)));
    }
}
