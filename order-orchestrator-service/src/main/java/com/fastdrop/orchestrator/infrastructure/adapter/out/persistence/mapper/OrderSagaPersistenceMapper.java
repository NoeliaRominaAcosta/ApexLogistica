package com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.mapper;

import com.fastdrop.orchestrator.domain.model.OrderSaga;
import com.fastdrop.orchestrator.infrastructure.adapter.out.persistence.entity.OrderSagaJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderSagaPersistenceMapper {

    public OrderSaga toDomain(OrderSagaJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return OrderSaga.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .customerId(entity.getCustomerId())
                .sku(entity.getSku())
                .quantity(entity.getQuantity())
                .warehouseId(entity.getWarehouseId())
                .deliveryAddress(entity.getDeliveryAddress())
                .shipmentId(entity.getShipmentId())
                .trackingNumber(entity.getTrackingNumber())
                .driverId(entity.getDriverId())
                .failureReason(entity.getFailureReason())
                .state(entity.getState())
                .deadline(entity.getDeadline())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public OrderSagaJpaEntity toEntity(OrderSaga domain) {
        if (domain == null) {
            return null;
        }
        return OrderSagaJpaEntity.builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .customerId(domain.getCustomerId())
                .sku(domain.getSku())
                .quantity(domain.getQuantity())
                .warehouseId(domain.getWarehouseId())
                .deliveryAddress(domain.getDeliveryAddress())
                .shipmentId(domain.getShipmentId())
                .trackingNumber(domain.getTrackingNumber())
                .driverId(domain.getDriverId())
                .failureReason(domain.getFailureReason())
                .state(domain.getState())
                .deadline(domain.getDeadline())
                .version(domain.getVersion())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
