package com.fastdrop.warehouse.infrastructure.adapter.out.persistence.mapper;

import com.fastdrop.warehouse.domain.model.InventoryItem;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.entity.InventoryItemJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class InventoryPersistenceMapper {

    public InventoryItem toDomain(InventoryItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return InventoryItem.builder()
                .id(entity.getId())
                .sku(entity.getSku())
                .warehouseId(entity.getWarehouseId())
                .totalStock(entity.getTotalStock())
                .reservedStock(entity.getReservedStock())
                .version(entity.getVersion())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public InventoryItemJpaEntity toEntity(InventoryItem domain) {
        if (domain == null) {
            return null;
        }
        return InventoryItemJpaEntity.builder()
                .id(domain.getId())
                .sku(domain.getSku())
                .warehouseId(domain.getWarehouseId())
                .totalStock(domain.getTotalStock())
                .reservedStock(domain.getReservedStock())
                .version(domain.getVersion())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
