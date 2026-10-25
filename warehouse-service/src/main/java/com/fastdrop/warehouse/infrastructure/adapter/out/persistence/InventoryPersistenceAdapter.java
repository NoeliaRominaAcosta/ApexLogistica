package com.fastdrop.warehouse.infrastructure.adapter.out.persistence;

import com.fastdrop.warehouse.application.port.out.InventoryRepositoryPort;
import com.fastdrop.warehouse.domain.model.InventoryItem;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.fastdrop.warehouse.infrastructure.adapter.out.persistence.repository.SpringDataInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    private final SpringDataInventoryRepository repository;
    private final InventoryPersistenceMapper mapper;

    @Override
    public Optional<InventoryItem> findBySkuAndWarehouseId(String sku, String warehouseId) {
        return repository.findBySkuAndWarehouseId(sku, warehouseId).map(mapper::toDomain);
    }

    @Override
    public Optional<InventoryItem> findFirstBySku(String sku) {
        return repository.findFirstBySku(sku).map(mapper::toDomain);
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        return mapper.toDomain(repository.save(mapper.toEntity(item)));
    }
}
