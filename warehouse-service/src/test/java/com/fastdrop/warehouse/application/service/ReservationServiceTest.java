package com.fastdrop.warehouse.application.service;

import com.fastdrop.warehouse.application.port.in.command.CancelReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ConfirmReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ReserveStockCommand;
import com.fastdrop.warehouse.application.port.out.DomainEventPublisherPort;
import com.fastdrop.warehouse.application.port.out.InventoryRepositoryPort;
import com.fastdrop.warehouse.application.port.out.ReservationRepositoryPort;
import com.fastdrop.warehouse.domain.event.StockReleasedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReservationRejectedDomainEvent;
import com.fastdrop.warehouse.domain.event.StockReservedDomainEvent;
import com.fastdrop.warehouse.domain.event.WarehouseDomainEvent;
import com.fastdrop.warehouse.domain.model.InventoryItem;
import com.fastdrop.warehouse.domain.model.ReservationState;
import com.fastdrop.warehouse.domain.model.StockReservation;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.ReservationStateMachineAdapter;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.ReservationStateMachineConfig;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.ConfirmReservationAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.RejectReservationAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.ReleaseStockAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.ReserveStockAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.guard.HasAvailableStockGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        ReservationStateMachineConfig.class,
        ReservationStateMachineAdapter.class,
        HasAvailableStockGuard.class,
        ReserveStockAction.class,
        RejectReservationAction.class,
        ConfirmReservationAction.class,
        ReleaseStockAction.class,
        ReservationService.class
})
class ReservationServiceTest {

    @MockBean
    private ReservationRepositoryPort reservationRepository;

    @MockBean
    private InventoryRepositoryPort inventoryRepository;

    @MockBean
    private DomainEventPublisherPort eventPublisher;

    @Autowired
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        when(reservationRepository.save(any(StockReservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(inventoryRepository.save(any(InventoryItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("NEW -> STOCK_CHECK -> RESERVED: reserva stock disponible y emite StockReservedDomainEvent")
    @SuppressWarnings("unchecked")
    void shouldReserveStockAndTransitionToReserved() {
        InventoryItem item = InventoryItem.builder()
                .sku("LAPTOP-01")
                .warehouseId("WH-CENTRAL-01")
                .totalStock(10)
                .reservedStock(2)
                .build();

        when(reservationRepository.findByOrderId("ORD-100")).thenReturn(Optional.empty());
        when(inventoryRepository.findBySkuAndWarehouseId("LAPTOP-01", "WH-CENTRAL-01")).thenReturn(Optional.of(item));

        StockReservation result = reservationService.reserve(
                new ReserveStockCommand("ORD-100", "LAPTOP-01", 3, "WH-CENTRAL-01", "Av. Corrientes 1234")
        );

        assertThat(result.getState()).isEqualTo(ReservationState.RESERVED);
        assertThat(item.getReservedStock()).isEqualTo(5);
        assertThat(item.getAvailableStock()).isEqualTo(5);

        ArgumentCaptor<List<WarehouseDomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst()).isInstanceOf(StockReservedDomainEvent.class);
    }

    @Test
    @DisplayName("NEW -> STOCK_CHECK -> REJECTED: rechaza reserva cuando no hay stock suficiente y persiste REJECTED")
    @SuppressWarnings("unchecked")
    void shouldRejectReservationWhenStockIsInsufficient() {
        InventoryItem item = InventoryItem.builder()
                .sku("LAPTOP-01")
                .warehouseId("WH-CENTRAL-01")
                .totalStock(2)
                .reservedStock(2)
                .build();

        when(reservationRepository.findByOrderId("ORD-101")).thenReturn(Optional.empty());
        when(inventoryRepository.findBySkuAndWarehouseId("LAPTOP-01", "WH-CENTRAL-01")).thenReturn(Optional.of(item));

        StockReservation result = reservationService.reserve(
                new ReserveStockCommand("ORD-101", "LAPTOP-01", 1, "WH-CENTRAL-01", "Calle Falsa 123")
        );

        assertThat(result.getState()).isEqualTo(ReservationState.REJECTED);
        assertThat(item.getReservedStock()).isEqualTo(2);
        verify(inventoryRepository, never()).save(any());

        ArgumentCaptor<List<WarehouseDomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getFirst()).isInstanceOf(StockReservationRejectedDomainEvent.class);
    }

    @Test
    @DisplayName("RESERVED -> CONFIRMED -> CANCELLED: si la entrega falla incluso tras confirmar, devuelve stock al warehouse")
    @SuppressWarnings("unchecked")
    void shouldConfirmAndThenCancelReturningStockToWarehouse() {
        InventoryItem item = InventoryItem.builder()
                .sku("LAPTOP-01")
                .warehouseId("WH-CENTRAL-01")
                .totalStock(10)
                .reservedStock(4)
                .build();

        StockReservation reserved = StockReservation.builder()
                .orderId("ORD-102")
                .warehouseId("WH-CENTRAL-01")
                .sku("LAPTOP-01")
                .quantity(4)
                .state(ReservationState.RESERVED)
                .build();

        when(reservationRepository.findByOrderId("ORD-102")).thenReturn(Optional.of(reserved));
        when(inventoryRepository.findBySkuAndWarehouseId("LAPTOP-01", "WH-CENTRAL-01")).thenReturn(Optional.of(item));

        StockReservation confirmed = reservationService.confirm(new ConfirmReservationCommand("ORD-102", "TRK-999"));
        assertThat(confirmed.getState()).isEqualTo(ReservationState.CONFIRMED);
        assertThat(confirmed.getTrackingNumber()).isEqualTo("TRK-999");

        StockReservation cancelled = reservationService.cancel(
                new CancelReservationCommand("ORD-102", "Fallo en entrega durante tránsito")
        );
        assertThat(cancelled.getState()).isEqualTo(ReservationState.CANCELLED);
        assertThat(item.getReservedStock()).isZero();
        assertThat(item.getAvailableStock()).isEqualTo(10);

        ArgumentCaptor<List<WarehouseDomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues().get(1).getFirst()).isInstanceOf(StockReleasedDomainEvent.class);
    }
}
