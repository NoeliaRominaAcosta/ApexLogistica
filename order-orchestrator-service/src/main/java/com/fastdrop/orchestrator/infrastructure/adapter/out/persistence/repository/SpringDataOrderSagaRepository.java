package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.repository;

import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.OrderSagaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataOrderSagaRepository extends JpaRepository<OrderSagaJpaEntity, UUID> {
    Optional<OrderSagaJpaEntity> findByOrderId(String orderId);

    @Query(value = "SELECT * FROM order_sagas WHERE deadline IS NOT NULL AND deadline < :now ORDER BY deadline ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OrderSagaJpaEntity> findExpiredSagasForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    List<OrderSagaJpaEntity> findTop50ByDeadlineBeforeOrderByDeadlineAsc(Instant now);
}
