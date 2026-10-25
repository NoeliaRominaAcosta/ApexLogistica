package com.fastdrop.dispatch.application.service;

import com.fastdrop.dispatch.application.port.in.command.DeliverShipmentCommand;
import com.fastdrop.dispatch.application.port.in.command.PickUpShipmentCommand;
import com.fastdrop.dispatch.application.port.in.command.ReportDeliveryFailureCommand;
import com.fastdrop.dispatch.application.port.in.command.ScheduleShipmentCommand;
import com.fastdrop.dispatch.application.port.out.DomainEventPublisherPort;
import com.fastdrop.dispatch.application.port.out.RouteResult;
import com.fastdrop.dispatch.application.port.out.RoutingPort;
import com.fastdrop.dispatch.application.port.out.ShipmentRepositoryPort;
import com.fastdrop.dispatch.domain.event.DeliveryFailedDomainEvent;
import com.fastdrop.dispatch.domain.event.DeliveryScheduledDomainEvent;
import com.fastdrop.dispatch.domain.event.DispatchDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentDeliveredDomainEvent;
import com.fastdrop.dispatch.domain.event.ShipmentPickedUpDomainEvent;
import com.fastdrop.dispatch.domain.model.RouteAssignment;
import com.fastdrop.dispatch.domain.model.Shipment;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.ShipmentStateMachineAdapter;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.ShipmentStateMachineConfig;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.action.*;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.guard.DriverMatchesGuard;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.guard.RouteResultPresentGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        ShipmentStateMachineConfig.class,
        ShipmentStateMachineAdapter.class,
        RouteResultPresentGuard.class,
        DriverMatchesGuard.class,
        AssignRouteAction.class,
        MarkShipmentFailedAction.class,
        PickUpShipmentAction.class,
        DeliverShipmentAction.class,
        CancelShipmentAction.class,
        ShipmentService.class,
        ShipmentServiceTest.TxConfig.class
})
class ShipmentServiceTest {

    @TestConfiguration
    static class TxConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }
                @Override public void commit(TransactionStatus status) {}
                @Override public void rollback(TransactionStatus status) {}
            };
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
            return new TransactionTemplate(txManager);
        }
    }

    @MockBean
    private ShipmentRepositoryPort shipmentRepository;

    @MockBean
    private RoutingPort routingPort;

    @MockBean
    private DomainEventPublisherPort eventPublisher;

    @Autowired
    private ShipmentService shipmentService;

    private final Map<String, Shipment> inMemoryStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        inMemoryStore.clear();
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            inMemoryStore.put(s.getOrderId(), s);
            return s;
        });
        when(shipmentRepository.findByOrderId(anyString())).thenAnswer(inv ->
                Optional.ofNullable(inMemoryStore.get(inv.<String>getArgument(0))));
    }

    @Test
    @DisplayName("PENDING_ROUTING -> SCHEDULED -> IN_TRANSIT -> DELIVERED: ciclo completo exitoso")
    @SuppressWarnings("unchecked")
    void shouldCompleteFullShipmentLifecycle() {
        when(routingPort.calculateRouteAndAssignDriver("ORD-200", "Av. Santa Fe 1000"))
                .thenReturn(new RouteResult(
                        true,
                        new RouteAssignment("DRV-500", "TRK-200", "Hoy 19:00", "https://labels/ORD-200.pdf"),
                        null,
                        false
                ));

        Shipment scheduled = shipmentService.schedule(
                new ScheduleShipmentCommand("ORD-200", "WH-CENTRAL-01", "Av. Santa Fe 1000")
        );
        assertThat(scheduled.getState()).isEqualTo(ShipmentState.SCHEDULED);
        assertThat(scheduled.getDriverId()).isEqualTo("DRV-500");

        Shipment pickedUp = shipmentService.pickUp(new PickUpShipmentCommand("ORD-200", "DRV-500"));
        assertThat(pickedUp.getState()).isEqualTo(ShipmentState.IN_TRANSIT);

        Shipment delivered = shipmentService.deliver(new DeliverShipmentCommand("ORD-200"));
        assertThat(delivered.getState()).isEqualTo(ShipmentState.DELIVERED);

        ArgumentCaptor<List<DispatchDomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(3)).publish(captor.capture());
        assertThat(captor.getAllValues().get(0).getFirst()).isInstanceOf(DeliveryScheduledDomainEvent.class);
        assertThat(captor.getAllValues().get(1).getFirst()).isInstanceOf(ShipmentPickedUpDomainEvent.class);
        assertThat(captor.getAllValues().get(2).getFirst()).isInstanceOf(ShipmentDeliveredDomainEvent.class);
    }

    @Test
    @DisplayName("PENDING_ROUTING -> FAILED: emite DeliveryFailedDomainEvent cuando falla el ruteo")
    @SuppressWarnings("unchecked")
    void shouldTransitionToFailedWhenRoutingFails() {
        when(routingPort.calculateRouteAndAssignDriver("ORD-201", "Zona Sin Cobertura FAIL"))
                .thenReturn(new RouteResult(false, null, "Sin flota disponible", true));

        Shipment failed = shipmentService.schedule(
                new ScheduleShipmentCommand("ORD-201", "WH-CENTRAL-01", "Zona Sin Cobertura FAIL")
        );
        assertThat(failed.getState()).isEqualTo(ShipmentState.FAILED);

        ArgumentCaptor<List<DispatchDomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getFirst()).isInstanceOf(DeliveryFailedDomainEvent.class);
    }

    @Test
    @DisplayName("IN_TRANSIT -> FAILED: falla durante el tránsito emite DeliveryFailedDomainEvent para compensar stock")
    @SuppressWarnings("unchecked")
    void shouldTransitionFromInTransitToFailedAndEmitDeliveryFailed() {
        when(routingPort.calculateRouteAndAssignDriver("ORD-202", "Av. Libertador 500"))
                .thenReturn(new RouteResult(
                        true,
                        new RouteAssignment("DRV-777", "TRK-202", "Hoy 20:00", "https://labels/ORD-202.pdf"),
                        null,
                        false
                ));

        shipmentService.schedule(new ScheduleShipmentCommand("ORD-202", "WH-CENTRAL-01", "Av. Libertador 500"));
        shipmentService.pickUp(new PickUpShipmentCommand("ORD-202", "DRV-777"));

        Shipment failedInTransit = shipmentService.reportFailure(
                new ReportDeliveryFailureCommand("ORD-202", "Dirección inexistente")
        );
        assertThat(failedInTransit.getState()).isEqualTo(ShipmentState.FAILED);

        ArgumentCaptor<List<DispatchDomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(3)).publish(captor.capture());
        assertThat(captor.getAllValues().get(2).getFirst()).isInstanceOf(DeliveryFailedDomainEvent.class);
    }
}
