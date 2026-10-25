package com.fastdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.event.StockReservedEvent;
import com.fastdrop.dispatch.client.ExternalRoutingServiceClient;
import com.fastdrop.dispatch.client.RouteAssignmentResult;
import com.fastdrop.dispatch.domain.OutboxEvent;
import com.fastdrop.dispatch.domain.Shipment;
import com.fastdrop.dispatch.repository.OutboxEventRepository;
import com.fastdrop.dispatch.repository.ShipmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ExternalRoutingServiceClient routingServiceClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private DispatchService dispatchService;

    @Test
    @DisplayName("Debe coordinar despacho exitoso y emitir DeliveryScheduled al Outbox")
    void shouldProcessStockReservedSuccessfully() {
        StockReservedEvent event = StockReservedEvent.builder()
                .orderId("ORD-2001")
                .warehouseId("WH-CENTRAL-01")
                .deliveryAddress("Av. Cabildo 2000, CABA")
                .quantity(1)
                .sku("SKU-MOUSE-01")
                .build();

        when(shipmentRepository.findByOrderId("ORD-2001")).thenReturn(Optional.empty());

        RouteAssignmentResult routeResult = RouteAssignmentResult.builder()
                .successful(true)
                .driverId("DRV-999")
                .trackingNumber("TRK-12345678")
                .estimatedDelivery("Hoy 18:00")
                .shippingLabelUrl("https://labels.fastdrop.internal/shipments/ORD-2001.pdf")
                .build();

        when(routingServiceClient.calculateRouteAndAssignDriver(eq("ORD-2001"), eq("Av. Cabildo 2000, CABA")))
                .thenReturn(routeResult);

        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> {
            Shipment s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        dispatchService.processStockReserved(event);

        ArgumentCaptor<Shipment> shipmentCaptor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(shipmentCaptor.capture());
        Shipment savedShipment = shipmentCaptor.getValue();
        assertThat(savedShipment.getStatus()).isEqualTo(Shipment.ShipmentStatus.SCHEDULED);
        assertThat(savedShipment.getDriverId()).isEqualTo("DRV-999");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("DeliveryScheduled");
        assertThat(outbox.getAggregateId()).isEqualTo("ORD-2001");
    }

    @Test
    @DisplayName("Debe emitir DeliveryFailed al Outbox para activar compensación de Saga cuando falla enrutamiento")
    void shouldEmitDeliveryFailedWhenRoutingFails() {
        StockReservedEvent event = StockReservedEvent.builder()
                .orderId("ORD-2002")
                .warehouseId("WH-CENTRAL-01")
                .deliveryAddress("Zona Remota Sin Cobertura")
                .quantity(1)
                .sku("SKU-MOUSE-01")
                .build();

        when(shipmentRepository.findByOrderId("ORD-2002")).thenReturn(Optional.empty());

        RouteAssignmentResult failedResult = RouteAssignmentResult.builder()
                .successful(false)
                .failureReason("No hay conductores disponibles")
                .fallbackTriggered(true)
                .build();

        when(routingServiceClient.calculateRouteAndAssignDriver(eq("ORD-2002"), eq("Zona Remota Sin Cobertura")))
                .thenReturn(failedResult);

        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> {
            Shipment s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        dispatchService.processStockReserved(event);

        ArgumentCaptor<Shipment> shipmentCaptor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(shipmentCaptor.capture());
        Shipment savedShipment = shipmentCaptor.getValue();
        assertThat(savedShipment.getStatus()).isEqualTo(Shipment.ShipmentStatus.FAILED);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("DeliveryFailed");
        assertThat(outbox.getAggregateId()).isEqualTo("ORD-2002");
    }
}
