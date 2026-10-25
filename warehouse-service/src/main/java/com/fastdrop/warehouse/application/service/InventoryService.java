package com.fastdrop.warehouse.application.service;

import com.fastdrop.warehouse.application.port.in.ManageInventoryUseCase;
import com.fastdrop.warehouse.application.port.out.InventoryRepositoryPort;
import com.fastdrop.warehouse.domain.model.InventoryItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService implements ManageInventoryUseCase {

    private final InventoryRepositoryPort inventoryRepository;

    @Override
    @Transactional
    public InventoryItem initializeInventory(String sku, String warehouseId, int totalStock) {
        String effectiveWarehouse = (warehouseId != null && !warehouseId.isBlank()) ? warehouseId : "WH-CENTRAL-01";
        log.info("Inicializando inventario: SKU={}, Warehouse={}, TotalStock={}", sku, effectiveWarehouse, totalStock);

        InventoryItem item = inventoryRepository.findBySkuAndWarehouseId(sku, effectiveWarehouse)
                .map(existing -> {
                    existing.setTotalStock(totalStock);
                    return existing;
                })
                .orElseGet(() -> InventoryItem.builder()
                        .sku(sku)
                        .warehouseId(effectiveWarehouse)
                        .totalStock(totalStock)
                        .reservedStock(0)
                        .build());

        return inventoryRepository.save(item);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItem> findBySku(String sku) {
        return inventoryRepository.findFirstBySku(sku);
    }
}
