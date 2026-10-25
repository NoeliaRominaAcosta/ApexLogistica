package com.fastdrop.warehouse.application.port.out;

import com.fastdrop.warehouse.domain.model.InventoryItem;

import java.util.Optional;

public interface InventoryRepositoryPort {
    Optional<InventoryItem> findBySkuAndWarehouseId(String sku, String warehouseId);
    Optional<InventoryItem> findFirstBySku(String sku);
    InventoryItem save(InventoryItem item);
}
