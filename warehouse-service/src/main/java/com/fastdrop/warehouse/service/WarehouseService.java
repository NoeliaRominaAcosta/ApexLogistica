package com.fastdrop.warehouse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.event.StockCancelledEvent;
import com.fastdrop.common.event.StockReservedEvent;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.warehouse.domain.InventoryItem;
import com.fastdrop.warehouse.domain.OutboxEvent;
import com.fastdrop.warehouse.domain.StockReservation;
import com.fastdrop.warehouse.dto.ReserveStockRequest;
import com.fastdrop.warehouse.repository.InventoryRepository;
import com.fastdrop.warehouse.repository.OutboxEventRepository;
import com.fastdrop.warehouse.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public InventoryItem initializeInventory(String sku, String warehouseId, int totalStock) {
        log.info("Inicializando inventario: SKU={}, Warehouse={}, TotalStock={}", sku, warehouseId, totalStock);
        Optional<InventoryItem> existing = inventoryRepository.findBySkuAndWarehouseId(sku, warehouseId);
        InventoryItem item;
        if (existing.isPresent()) {
            item = existing.get();
            item.setTotalStock(totalStock);
        } else {
            item = InventoryItem.builder()
                    .sku(sku)
                    .warehouseId(warehouseId)
                    .totalStock(totalStock)
                    .reservedStock(0)
                    .build();
        }
        return inventoryRepository.save(item);
    }

    @Transactional
    public StockReservation reserveStock(ReserveStockRequest request) {
        String traceParent = TraceUtils.getOrCreateTraceParent();
        log.info("Procesando reserva de stock para Orden={} [TraceParent={}]", request.getOrderId(), traceParent);

        // Validar si ya existe reserva para idempotencia
        Optional<StockReservation> existing = reservationRepository.findByOrderId(request.getOrderId());
        if (existing.isPresent()) {
            log.warn("La orden {} ya posee una reserva en estado {}", request.getOrderId(), existing.get().getStatus());
            return existing.get();
        }

        String warehouseId = (request.getWarehouseId() != null && !request.getWarehouseId().isBlank())
                ? request.getWarehouseId()
                : "WH-CENTRAL-01";

        InventoryItem inventoryItem = inventoryRepository.findBySkuAndWarehouseId(request.getSku(), warehouseId)
                .orElseGet(() -> inventoryRepository.findFirstBySku(request.getSku())
                        .orElseThrow(() -> new IllegalArgumentException("No se encontró inventario para SKU: " + request.getSku())));

        // Reserva con control de concurrencia optimista (Hibernate chequeará el @Version al hacer flush)
        inventoryItem.reserve(request.getQuantity());
        inventoryRepository.save(inventoryItem);

        StockReservation reservation = StockReservation.builder()
                .orderId(request.getOrderId())
                .warehouseId(inventoryItem.getWarehouseId())
                .sku(request.getSku())
                .quantity(request.getQuantity())
                .status(StockReservation.ReservationStatus.RESERVED)
                .build();
        reservation = reservationRepository.save(reservation);

        // Crear evento de dominio StockReserved
        StockReservedEvent event = StockReservedEvent.builder()
                .orderId(request.getOrderId())
                .warehouseId(inventoryItem.getWarehouseId())
                .sku(request.getSku())
                .quantity(request.getQuantity())
                .deliveryAddress(request.getDeliveryAddress())
                .occurredAt(Instant.now())
                .traceParent(traceParent)
                .build();

        // Transactional Outbox: guardar evento en la misma transacción de base de datos
        saveToOutbox("StockReservation", request.getOrderId(), "StockReserved", event, traceParent);

        log.info("Reserva exitosa y Outbox registrado para Orden={}", request.getOrderId());
        return reservation;
    }

    @Transactional
    public void cancelReservation(String orderId, String reason) {
        String traceParent = TraceUtils.getOrCreateTraceParent();
        log.warn("Ejecutando compensación de Saga: Cancelando reserva para Orden={}, Motivo={}", orderId, reason);

        Optional<StockReservation> reservationOpt = reservationRepository.findByOrderId(orderId);
        if (reservationOpt.isEmpty()) {
            log.warn("Compensación ignorada: No existe reserva para la orden {}", orderId);
            return;
        }

        StockReservation reservation = reservationOpt.get();
        if (reservation.getStatus() == StockReservation.ReservationStatus.CANCELLED) {
            log.info("La reserva para la orden {} ya fue cancelada previamente (Idempotencia)", orderId);
            return;
        }

        // Devolver stock al inventario
        inventoryRepository.findBySkuAndWarehouseId(reservation.getSku(), reservation.getWarehouseId())
                .ifPresent(item -> {
                    item.release(reservation.getQuantity());
                    inventoryRepository.save(item);
                });

        reservation.setStatus(StockReservation.ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        // Emitir evento compensatorio StockCancelled vía Outbox
        StockCancelledEvent event = StockCancelledEvent.builder()
                .orderId(orderId)
                .warehouseId(reservation.getWarehouseId())
                .sku(reservation.getSku())
                .quantity(reservation.getQuantity())
                .reason(reason)
                .occurredAt(Instant.now())
                .traceParent(traceParent)
                .build();

        saveToOutbox("StockReservation", orderId, "StockCancelled", event, traceParent);
        log.info("Compensación completada y Outbox registrado para orden {}", orderId);
    }

    private void saveToOutbox(String aggregateType, String aggregateId, String eventType, Object payload, String traceParent) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(jsonPayload)
                    .traceContext(traceParent)
                    .createdAt(Instant.now())
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Error al serializar payload de outbox para {}", aggregateId, e);
            throw new RuntimeException("Fallo al serializar evento outbox", e);
        }
    }
}
