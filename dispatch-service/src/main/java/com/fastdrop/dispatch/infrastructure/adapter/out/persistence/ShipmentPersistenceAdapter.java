package com.fastdrop.dispatch.infrastructure.adapter.out.persistence;

import com.fastdrop.dispatch.application.port.out.ShipmentRepositoryPort;
import com.fastdrop.dispatch.domain.model.Shipment;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.dispatch.infrastructure.adapter.out.persistence.mapper.ShipmentPersistenceMapper;
import com.fastdrop.dispatch.infrastructure.adapter.out.persistence.repository.SpringDataShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ShipmentPersistenceAdapter implements ShipmentRepositoryPort {

    private final SpringDataShipmentRepository repository;
    private final ShipmentPersistenceMapper mapper;

    @Override
    public Optional<Shipment> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public List<Shipment> findByStateAndCreatedAtBefore(ShipmentState state, Instant threshold) {
        return repository.findByStatusAndCreatedAtBefore(state, threshold)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Shipment save(Shipment shipment) {
        return mapper.toDomain(repository.save(mapper.toEntity(shipment)));
    }
}
