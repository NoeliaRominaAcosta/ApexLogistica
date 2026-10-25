package com.fastdrop.warehouse.infrastructure.adapter.out.persistence;

import com.fastdrop.warehouse.application.port.out.ReservationRepositoryPort;
import com.fastdrop.warehouse.domain.model.StockReservation;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.mapper.ReservationPersistenceMapper;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.repository.SpringDataReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements ReservationRepositoryPort {

    private final SpringDataReservationRepository repository;
    private final ReservationPersistenceMapper mapper;

    @Override
    public Optional<StockReservation> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public StockReservation save(StockReservation reservation) {
        return mapper.toDomain(repository.save(mapper.toEntity(reservation)));
    }
}
