package com.fastdrop.warehouse.domain.exception;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(String sku) {
        super("No se encontró inventario para SKU: " + sku);
    }
}
