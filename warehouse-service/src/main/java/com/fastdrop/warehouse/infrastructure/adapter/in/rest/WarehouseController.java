package com.fastdrop.warehouse.infrastructure.adapter.in.rest;

import com.fastdrop.warehouse.application.port.in.CancelReservationUseCase;
import com.fastdrop.warehouse.application.port.in.ConfirmReservationUseCase;
import com.fastdrop.warehouse.application.port.in.ManageInventoryUseCase;
import com.fastdrop.warehouse.application.port.in.ReserveStockUseCase;
import com.fastdrop.warehouse.application.port.in.command.CancelReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ConfirmReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ReserveStockCommand;
import com.fastdrop.warehouse.domain.model.InventoryItem;
import com.fastdrop.warehouse.domain.model.ReservationState;
import com.fastdrop.warehouse.domain.model.StockReservation;
import com.fastdrop.warehouse.infrastructure.adapter.in.rest.dto.ReserveStockRequest;
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

    private final ReserveStockUseCase reserveStockUseCase;
    private final ConfirmReservationUseCase confirmReservationUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final ManageInventoryUseCase manageInventoryUseCase;

    @PostMapping("/inventory/init")
    public ResponseEntity<InventoryItem> initInventory(@RequestBody InitInventoryRequest request) {
        InventoryItem item = manageInventoryUseCase.initializeInventory(
                request.getSku(),
                request.getWarehouseId() != null ? request.getWarehouseId() : "WH-CENTRAL-01",
                request.getTotalStock()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @GetMapping("/inventory/{sku}")
    public ResponseEntity<?> getInventory(@PathVariable String sku) {
        return manageInventoryUseCase.findBySku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/reserve")
    public ResponseEntity<StockReservation> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        StockReservation reservation = reserveStockUseCase.reserve(new ReserveStockCommand(
                request.getOrderId(),
                request.getSku(),
                request.getQuantity(),
                request.getWarehouseId(),
                request.getDeliveryAddress()
        ));
        HttpStatus status = reservation.getState() == ReservationState.REJECTED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(reservation);
    }

    @PostMapping("/orders/{orderId}/confirm")
    public ResponseEntity<StockReservation> confirmReservation(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "") String trackingNumber) {
        StockReservation reservation = confirmReservationUseCase.confirm(
                new ConfirmReservationCommand(orderId, trackingNumber)
        );
        if (reservation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(reservation);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Map<String, String>> cancelReservation(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Cancelación manual solicitada") String reason) {
        StockReservation cancelled = cancelReservationUseCase.cancel(new CancelReservationCommand(orderId, reason));
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", cancelled.getState().name(),
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
