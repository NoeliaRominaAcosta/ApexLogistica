package com.fastdrop.warehouse.application.port.in;

import com.fastdrop.warehouse.domain.model.InventoryItem;

import java.util.Optional;

public interface ManageInventoryUseCase {
    InventoryItem initializeInventory(String sku, String warehouseId, int totalStock);
    Optional<InventoryItem> findBySku(String sku);
}
