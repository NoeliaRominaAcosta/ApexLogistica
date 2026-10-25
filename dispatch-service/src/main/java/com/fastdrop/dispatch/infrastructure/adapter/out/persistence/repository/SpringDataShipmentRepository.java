package com.fastdrop.dispatch.infrastructure.adapter.out.persistence.repository;

import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.dispatch.infrastructure.adapter.out.persistence.entity.ShipmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataShipmentRepository extends JpaRepository<ShipmentJpaEntity, UUID> {
    Optional<ShipmentJpaEntity> findByOrderId(String orderId);
    List<ShipmentJpaEntity> findByStatusAndCreatedAtBefore(ShipmentState status, Instant threshold);
}
