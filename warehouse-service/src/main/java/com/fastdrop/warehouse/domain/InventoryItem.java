package com.fastdrop.warehouse.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sku_warehouse", columnNames = {"sku", "warehouse_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "warehouse_id", nullable = false, length = 64)
    private String warehouseId;

    @Column(nullable = false)
    private int totalStock;

    @Column(nullable = false)
    private int reservedStock;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public int getAvailableStock() {
        return totalStock - reservedStock;
    }

    public boolean hasAvailableStock(int requestedQuantity) {
        return getAvailableStock() >= requestedQuantity;
    }

    public void reserve(int quantity) {
        if (!hasAvailableStock(quantity)) {
            throw new IllegalStateException(String.format(
                    "Stock insuficiente para SKU %s en depósito %s. Disponible: %d, Solicitado: %d",
                    sku, warehouseId, getAvailableStock(), quantity
            ));
        }
        this.reservedStock += quantity;
    }

    public void release(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
    }
}
