package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.repository;

import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.ProcessedMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataProcessedMessageRepository extends JpaRepository<ProcessedMessageJpaEntity, String> {
}
