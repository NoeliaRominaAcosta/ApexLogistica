package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity;

import com.fastdrop.orchestrator.domain.model.SagaState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_sagas", indexes = {
        @Index(name = "idx_saga_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_saga_deadline", columnList = "deadline")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSagaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "warehouse_id", length = 64)
    private String warehouseId;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Column(name = "shipment_id", length = 64)
    private String shipmentId;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "driver_id", length = 64)
    private String driverId;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SagaState state;

    @Column(name = "deadline")
    private Instant deadline;

    @Version
    @Column
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
