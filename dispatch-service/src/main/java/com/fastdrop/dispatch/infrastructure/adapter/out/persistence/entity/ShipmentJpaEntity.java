package com.fastdrop.dispatch.infrastructure.adapter.out.persistence.entity;

import com.fastdrop.dispatch.domain.model.ShipmentState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipment_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_shipment_tracking_num", columnList = "tracking_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "tracking_number", nullable = false, length = 64)
    private String trackingNumber;

    @Column(name = "warehouse_id", length = 64)
    private String warehouseId;

    @Column(name = "driver_id", length = 64)
    private String driverId;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShipmentState status;

    @Column(name = "estimated_delivery")
    private String estimatedDelivery;

    @Column(name = "shipping_label_url", length = 512)
    private String shippingLabelUrl;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

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
