package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "processed_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessageJpaEntity {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
