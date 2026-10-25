package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.repository;

import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query(value = "SELECT * FROM outbox_events WHERE processed_at IS NULL ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventJpaEntity> findPendingEventsForProcessing(@Param("limit") int limit);

    List<OutboxEventJpaEntity> findTop50ByProcessedAtIsNullOrderByCreatedAtAsc();
}
