package com.fastdrop.warehouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.warehouse.domain.InventoryItem;
import com.fastdrop.warehouse.domain.OutboxEvent;
import com.fastdrop.warehouse.domain.StockReservation;
import com.fastdrop.warehouse.dto.ReserveStockRequest;
import com.fastdrop.warehouse.repository.InventoryRepository;
import com.fastdrop.warehouse.repository.OutboxEventRepository;
import com.fastdrop.warehouse.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private WarehouseService warehouseService;

    private InventoryItem sampleInventory;

    @BeforeEach
    void setUp() {
        sampleInventory = InventoryItem.builder()
                .id(1L)
                .sku("SKU-LAPTOP-01")
                .warehouseId("WH-CENTRAL-01")
                .totalStock(10)
                .reservedStock(2)
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Debe reservar stock exitosamente y registrar el evento en Outbox")
    void shouldReserveStockAndWriteOutbox() {
        ReserveStockRequest request = ReserveStockRequest.builder()
                .orderId("ORD-1001")
                .sku("SKU-LAPTOP-01")
                .warehouseId("WH-CENTRAL-01")
                .quantity(3)
                .deliveryAddress("Av. Corrientes 1234, CABA")
                .build();

        when(reservationRepository.findByOrderId("ORD-1001")).thenReturn(Optional.empty());
        when(inventoryRepository.findBySkuAndWarehouseId("SKU-LAPTOP-01", "WH-CENTRAL-01"))
                .thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(i -> i.getArgument(0));

        StockReservation reservation = warehouseService.reserveStock(request);

        assertThat(reservation).isNotNull();
        assertThat(reservation.getOrderId()).isEqualTo("ORD-1001");
        assertThat(reservation.getStatus()).isEqualTo(StockReservation.ReservationStatus.RESERVED);
        assertThat(sampleInventory.getReservedStock()).isEqualTo(5); // 2 + 3
        assertThat(sampleInventory.getAvailableStock()).isEqualTo(5); // 10 - 5

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getEventType()).isEqualTo("StockReserved");
        assertThat(savedOutbox.getAggregateId()).isEqualTo("ORD-1001");
    }

    @Test
    @DisplayName("Debe lanzar excepción si el stock disponible es insuficiente")
    void shouldThrowExceptionWhenStockInsufficient() {
        ReserveStockRequest request = ReserveStockRequest.builder()
                .orderId("ORD-1002")
                .sku("SKU-LAPTOP-01")
                .warehouseId("WH-CENTRAL-01")
                .quantity(99) // Supera los 8 disponibles
                .deliveryAddress("Av. Libertador 500, CABA")
                .build();

        when(reservationRepository.findByOrderId("ORD-1002")).thenReturn(Optional.empty());
        when(inventoryRepository.findBySkuAndWarehouseId("SKU-LAPTOP-01", "WH-CENTRAL-01"))
                .thenReturn(Optional.of(sampleInventory));

        assertThatThrownBy(() -> warehouseService.reserveStock(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe compensar reserva de stock (Rollback Saga) liberando inventario y escribiendo Outbox")
    void shouldCancelReservationAndCompensateStock() {
        StockReservation reservation = StockReservation.builder()
                .orderId("ORD-1003")
                .sku("SKU-LAPTOP-01")
                .warehouseId("WH-CENTRAL-01")
                .quantity(2)
                .status(StockReservation.ReservationStatus.RESERVED)
                .build();

        when(reservationRepository.findByOrderId("ORD-1003")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findBySkuAndWarehouseId("SKU-LAPTOP-01", "WH-CENTRAL-01"))
                .thenReturn(Optional.of(sampleInventory));

        warehouseService.cancelReservation("ORD-1003", "Sin chofer disponible");

        assertThat(reservation.getStatus()).isEqualTo(StockReservation.ReservationStatus.CANCELLED);
        assertThat(sampleInventory.getReservedStock()).isEqualTo(0); // 2 - 2

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("StockCancelled");
        assertThat(outbox.getAggregateId()).isEqualTo("ORD-1003");
    }
}
