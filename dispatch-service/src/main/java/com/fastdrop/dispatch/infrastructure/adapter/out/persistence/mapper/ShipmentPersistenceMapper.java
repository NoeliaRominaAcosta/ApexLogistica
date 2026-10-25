package com.fastdrop.dispatch.infrastructure.adapter.out.persistence.mapper;

import com.fastdrop.dispatch.domain.model.Shipment;
import com.fastdrop.dispatch.infrastructure.adapter.out.persistence.entity.ShipmentJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ShipmentPersistenceMapper {

    public Shipment toDomain(ShipmentJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Shipment.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .trackingNumber(entity.getTrackingNumber())
                .warehouseId(entity.getWarehouseId())
                .driverId(entity.getDriverId())
                .deliveryAddress(entity.getDeliveryAddress())
                .state(entity.getStatus())
                .estimatedDelivery(entity.getEstimatedDelivery())
                .shippingLabelUrl(entity.getShippingLabelUrl())
                .failureReason(entity.getFailureReason())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ShipmentJpaEntity toEntity(Shipment domain) {
        if (domain == null) {
            return null;
        }
        return ShipmentJpaEntity.builder()
                .id(domain.getId())
                .orderId(domain.getOrderId())
                .trackingNumber(domain.getTrackingNumber())
                .warehouseId(domain.getWarehouseId())
                .driverId(domain.getDriverId())
                .deliveryAddress(domain.getDeliveryAddress())
                .status(domain.getState())
                .estimatedDelivery(domain.getEstimatedDelivery())
                .shippingLabelUrl(domain.getShippingLabelUrl())
                .failureReason(domain.getFailureReason())
                .version(domain.getVersion())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
