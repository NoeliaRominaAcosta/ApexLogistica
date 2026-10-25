package com.fastdrop.warehouse.repository;

import com.fastdrop.warehouse.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {
    Optional<InventoryItem> findBySkuAndWarehouseId(String sku, String warehouseId);
    Optional<InventoryItem> findFirstBySku(String sku);
}
