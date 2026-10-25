package com.fastdrop.warehouse.infrastructure.adapter.out.persistence.repository;

import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.entity.InventoryItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataInventoryRepository extends JpaRepository<InventoryItemJpaEntity, Long> {
    Optional<InventoryItemJpaEntity> findBySkuAndWarehouseId(String sku, String warehouseId);
    Optional<InventoryItemJpaEntity> findFirstBySku(String sku);
}
