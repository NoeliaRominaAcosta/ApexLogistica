package com.fastdrop.warehouse.infrastructure.adapter.out.persistence.mapper;

import com.fastdrop.warehouse.domain.model.StockReservation;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.entity.StockReservationJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationPersistenceMapper {

    public StockReservation toDomain(StockReservationJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return StockReservation.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .warehouseId(entity.getWarehouseId())
                .sku(entity.getSku())
                .quantity(entity.getQuantity())
                .deliveryAddress(entity.getDeliveryAddress())
                .trackingNumber(entity.getTrackingNumber())
                .failureReason(entity.getFailureReason())
                .state(entity.getStatus())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public StockReservationJpaEntity toEntity(StockReservation domain) {
        if (domain == null) {
            return null;
        }
        return StockReservationJpaEntity.builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .warehouseId(domain.getWarehouseId())
                .sku(domain.getSku())
                .quantity(domain.getQuantity())
                .deliveryAddress(domain.getDeliveryAddress())
                .trackingNumber(domain.getTrackingNumber())
                .failureReason(domain.getFailureReason())
                .status(domain.getState())
                .version(domain.getVersion())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
