package com.fastdrop.orchestrator.application.service;

import com.fastdrop.orchestrator.application.port.in.command.CancelOrderSagaCommand;
import com.fastdrop.orchestrator.application.port.in.command.ParticipantReplyCommand;
import com.fastdrop.orchestrator.application.port.in.command.StartOrderSagaCommand;
import com.fastdrop.orchestrator.application.port.out.OrderSagaRepositoryPort;
import com.fastdrop.orchestrator.application.port.out.ProcessedMessagePort;
import com.fastdrop.orchestrator.application.port.out.SagaCommandPublisherPort;
import com.fastdrop.orchestrator.domain.command.*;
import com.fastdrop.orchestrator.domain.model.OrderSaga;
import com.fastdrop.orchestrator.domain.model.SagaEvent;
import com.fastdrop.orchestrator.domain.model.SagaState;
import com.fastdrop.orchestrator.infrastructure.adapter.out.statemachine.SagaStateMachineAdapter;
import com.fastdrop.orchestrator.infrastructure.adapter.out.statemachine.SagaStateMachineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        SagaStateMachineConfig.class,
        SagaStateMachineAdapter.class,
        OrderSagaService.class
})
class OrderSagaServiceTest {

    @MockBean
    private OrderSagaRepositoryPort sagaRepository;

    @MockBean
    private ProcessedMessagePort processedMessagePort;

    @MockBean
    private SagaCommandPublisherPort commandPublisher;

    @Autowired
    private OrderSagaService orderSagaService;

    private final Map<String, OrderSaga> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();
        when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> {
            OrderSaga s = inv.getArgument(0);
            store.put(s.getOrderId(), s);
            return s;
        });
        when(sagaRepository.findByOrderId(anyString())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(processedMessagePort.isAlreadyProcessed(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("Camino feliz completo: NEW -> RESERVING_STOCK -> SCHEDULING_DELIVERY -> AWAITING_PICKUP -> IN_TRANSIT -> COMPLETED")
    @SuppressWarnings("unchecked")
    void shouldCompleteHappyPathEndToEnd() {
        OrderSaga saga = orderSagaService.start(
                new StartOrderSagaCommand("ORD-300", "CUST-1", "LAPTOP-01", 1, "WH-CENTRAL-01", "Av. Cabildo 2000")
        );
        assertThat(saga.getState()).isEqualTo(SagaState.RESERVING_STOCK);

        saga = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-1", "ORD-300", SagaEvent.STOCK_RESERVED,
                Map.of(OrderSagaService.HEADER_WAREHOUSE_ID, "WH-CENTRAL-01")
        ));
        assertThat(saga.getState()).isEqualTo(SagaState.SCHEDULING_DELIVERY);

        saga = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-2", "ORD-300", SagaEvent.DELIVERY_SCHEDULED,
                Map.of(
                        OrderSagaService.HEADER_SHIPMENT_ID, "SHP-1",
                        OrderSagaService.HEADER_TRACKING_NUMBER, "TRK-300",
                        OrderSagaService.HEADER_DRIVER_ID, "DRV-101"
                )
        ));
        assertThat(saga.getState()).isEqualTo(SagaState.AWAITING_PICKUP);
        assertThat(saga.getTrackingNumber()).isEqualTo("TRK-300");

        saga = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-3", "ORD-300", SagaEvent.SHIPMENT_PICKED_UP,
                Map.of(OrderSagaService.HEADER_DRIVER_ID, "DRV-101")
        ));
        assertThat(saga.getState()).isEqualTo(SagaState.IN_TRANSIT);

        saga = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-4", "ORD-300", SagaEvent.SHIPMENT_DELIVERED, Map.of()
        ));
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(saga.getDeadline()).isNull();

        ArgumentCaptor<List<SagaOutboundCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandPublisher, times(5)).publish(captor.capture());
        assertThat(captor.getAllValues().get(0).getFirst()).isInstanceOf(ReserveStockSagaCommand.class);
        assertThat(captor.getAllValues().get(1).getFirst()).isInstanceOf(ScheduleDeliverySagaCommand.class);
        assertThat(captor.getAllValues().get(2).getFirst()).isInstanceOf(ConfirmReservationSagaCommand.class);
    }

    @Test
    @DisplayName("Falla de entrega en tránsito (D10): IN_TRANSIT --DELIVERY_FAILED--> RELEASING_STOCK --STOCK_RELEASED--> CANCELLED")
    @SuppressWarnings("unchecked")
    void shouldCompensateAndReleaseStockWhenDeliveryFailsInTransit() {
        orderSagaService.start(
                new StartOrderSagaCommand("ORD-301", "CUST-2", "LAPTOP-01", 2, "WH-CENTRAL-01", "Zona Sur 500")
        );
        orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-10", "ORD-301", SagaEvent.STOCK_RESERVED, Map.of()
        ));
        orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-11", "ORD-301", SagaEvent.DELIVERY_SCHEDULED,
                Map.of(OrderSagaService.HEADER_TRACKING_NUMBER, "TRK-301")
        ));
        orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-12", "ORD-301", SagaEvent.SHIPMENT_PICKED_UP, Map.of()
        ));

        OrderSaga releasing = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-13", "ORD-301", SagaEvent.DELIVERY_FAILED,
                Map.of(OrderSagaService.HEADER_REASON, "Domicilio inaccesible")
        ));
        assertThat(releasing.getState()).isEqualTo(SagaState.RELEASING_STOCK);

        OrderSaga cancelled = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-14", "ORD-301", SagaEvent.STOCK_RELEASED, Map.of()
        ));
        assertThat(cancelled.getState()).isEqualTo(SagaState.CANCELLED);

        ArgumentCaptor<List<SagaOutboundCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandPublisher, times(6)).publish(captor.capture());
        assertThat(captor.getAllValues().get(4).getFirst()).isInstanceOf(ReleaseStockSagaCommand.class);
    }

    @Test
    @DisplayName("Cancelación manual en AWAITING_PICKUP: cancela envío en Dispatch y luego libera stock en Warehouse")
    @SuppressWarnings("unchecked")
    void shouldCancelShipmentAndThenReleaseStockWhenCancelledBeforePickup() {
        orderSagaService.start(
                new StartOrderSagaCommand("ORD-302", "CUST-3", "LAPTOP-01", 1, "WH-CENTRAL-01", "Av. Corrientes 500")
        );
        orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-20", "ORD-302", SagaEvent.STOCK_RESERVED, Map.of()
        ));
        orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-21", "ORD-302", SagaEvent.DELIVERY_SCHEDULED,
                Map.of(OrderSagaService.HEADER_TRACKING_NUMBER, "TRK-302")
        ));

        OrderSaga cancelling = orderSagaService.cancel(
                new CancelOrderSagaCommand("ORD-302", "Cliente arrepentido")
        );
        assertThat(cancelling.getState()).isEqualTo(SagaState.CANCELLING_SHIPMENT);

        OrderSaga releasing = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-22", "ORD-302", SagaEvent.SHIPMENT_CANCELLED, Map.of()
        ));
        assertThat(releasing.getState()).isEqualTo(SagaState.RELEASING_STOCK);

        OrderSaga cancelled = orderSagaService.onParticipantReply(new ParticipantReplyCommand(
                "evt-23", "ORD-302", SagaEvent.STOCK_RELEASED, Map.of()
        ));
        assertThat(cancelled.getState()).isEqualTo(SagaState.CANCELLED);

        ArgumentCaptor<List<SagaOutboundCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(commandPublisher, times(6)).publish(captor.capture());
        assertThat(captor.getAllValues().get(3).getFirst()).isInstanceOf(CancelShipmentSagaCommand.class);
        assertThat(captor.getAllValues().get(4).getFirst()).isInstanceOf(ReleaseStockSagaCommand.class);
    }
}
