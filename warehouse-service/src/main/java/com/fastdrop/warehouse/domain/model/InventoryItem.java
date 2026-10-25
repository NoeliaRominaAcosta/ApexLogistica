package com.fastdrop.warehouse.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Modelo de dominio puro para un ítem de inventario (libre de anotaciones JPA).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    private Long id;
    private String sku;
    private String warehouseId;
    private int totalStock;
    private int reservedStock;
    private Long version;
    private Instant updatedAt;

    public int getAvailableStock() {
        return totalStock - reservedStock;
    }

    public boolean hasAvailableStock(int requestedQuantity) {
        return requestedQuantity > 0 && getAvailableStock() >= requestedQuantity;
    }

    public void reserve(int quantity) {
        if (!hasAvailableStock(quantity)) {
            throw new IllegalStateException(String.format(
                    "Stock insuficiente para SKU %s en depósito %s. Disponible: %d, Solicitado: %d",
                    sku, warehouseId, getAvailableStock(), quantity
            ));
        }
        this.reservedStock += quantity;
        this.updatedAt = Instant.now();
    }

    public void release(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
        this.updatedAt = Instant.now();
    }
}
