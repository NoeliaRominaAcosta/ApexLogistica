package com.fastdrop.warehouse.controller;

import com.fastdrop.warehouse.domain.InventoryItem;
import com.fastdrop.warehouse.domain.StockReservation;
import com.fastdrop.warehouse.dto.ReserveStockRequest;
import com.fastdrop.warehouse.repository.InventoryRepository;
import com.fastdrop.warehouse.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final InventoryRepository inventoryRepository;

    @PostMapping("/inventory/init")
    public ResponseEntity<InventoryItem> initInventory(@RequestBody InitInventoryRequest request) {
        InventoryItem item = warehouseService.initializeInventory(
                request.getSku(),
                request.getWarehouseId() != null ? request.getWarehouseId() : "WH-CENTRAL-01",
                request.getTotalStock()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @GetMapping("/inventory/{sku}")
    public ResponseEntity<?> getInventory(@PathVariable String sku) {
        return inventoryRepository.findFirstBySku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/reserve")
    public ResponseEntity<StockReservation> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        StockReservation reservation = warehouseService.reserveStock(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reservation);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Map<String, String>> cancelReservation(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Cancelación manual solicitada") String reason) {
        warehouseService.cancelReservation(orderId, reason);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "CANCELLED",
                "reason", reason
        ));
    }

    @Data
    public static class InitInventoryRequest {
        private String sku;
        private String warehouseId;
        private int totalStock;
    }
}
