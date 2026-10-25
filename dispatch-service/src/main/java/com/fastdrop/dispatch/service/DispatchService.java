package com.fastdrop.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.event.DeliveryFailedEvent;
import com.fastdrop.common.event.DeliveryScheduledEvent;
import com.fastdrop.common.event.StockReservedEvent;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.dispatch.client.ExternalRoutingServiceClient;
import com.fastdrop.dispatch.client.RouteAssignmentResult;
import com.fastdrop.dispatch.domain.OutboxEvent;
import com.fastdrop.dispatch.domain.Shipment;
import com.fastdrop.dispatch.repository.OutboxEventRepository;
import com.fastdrop.dispatch.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final ShipmentRepository shipmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ExternalRoutingServiceClient routingServiceClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processStockReserved(StockReservedEvent event) {
        String traceParent = event.getTraceParent() != null ? event.getTraceParent() : TraceUtils.getOrCreateTraceParent();
        TraceUtils.setTraceContext(traceParent);

        try {
            log.info("Iniciando despacho para Orden={} desde depósito={}", event.getOrderId(), event.getWarehouseId());

            // Idempotencia
            Optional<Shipment> existing = shipmentRepository.findByOrderId(event.getOrderId());
            if (existing.isPresent()) {
                log.warn("El despacho para la orden {} ya existe con estado {}", event.getOrderId(), existing.get().getStatus());
                return;
            }

            // Llamada protegida por Resilience4j
            RouteAssignmentResult routeResult = routingServiceClient.calculateRouteAndAssignDriver(
                    event.getOrderId(),
                    event.getDeliveryAddress()
            );

            if (routeResult.isSuccessful()) {
                Shipment shipment = Shipment.builder()
                        .orderId(event.getOrderId())
                        .warehouseId(event.getWarehouseId())
                        .deliveryAddress(event.getDeliveryAddress())
                        .trackingNumber(routeResult.getTrackingNumber())
                        .driverId(routeResult.getDriverId())
                        .estimatedDelivery(routeResult.getEstimatedDelivery())
                        .shippingLabelUrl(routeResult.getShippingLabelUrl())
                        .status(Shipment.ShipmentStatus.SCHEDULED)
                        .build();

                shipmentRepository.save(shipment);

                DeliveryScheduledEvent scheduledEvent = DeliveryScheduledEvent.builder()
                        .shipmentId(shipment.getId().toString())
                        .orderId(event.getOrderId())
                        .trackingNumber(routeResult.getTrackingNumber())
                        .driverId(routeResult.getDriverId())
                        .estimatedDeliveryTime(routeResult.getEstimatedDelivery())
                        .shippingLabelUrl(routeResult.getShippingLabelUrl())
                        .occurredAt(Instant.now())
                        .traceParent(traceParent)
                        .build();

                saveToOutbox("Shipment", event.getOrderId(), "DeliveryScheduled", scheduledEvent, traceParent);
                log.info("Despacho programado con éxito para orden {}. Outbox registrado.", event.getOrderId());
            } else {
                // Saga Compensación Trigger: Falló asignación de conductor o ruta
                log.warn("Fallo en despacho para orden {}. Motivo: {}. Disparando compensación Saga.",
                        event.getOrderId(), routeResult.getFailureReason());

                Shipment shipment = Shipment.builder()
                        .orderId(event.getOrderId())
                        .warehouseId(event.getWarehouseId())
                        .deliveryAddress(event.getDeliveryAddress())
                        .trackingNumber("UNASSIGNED")
                        .status(Shipment.ShipmentStatus.FAILED)
                        .failureReason(routeResult.getFailureReason())
                        .build();

                shipmentRepository.save(shipment);

                DeliveryFailedEvent failedEvent = DeliveryFailedEvent.builder()
                        .orderId(event.getOrderId())
                        .reason(routeResult.getFailureReason())
                        .occurredAt(Instant.now())
                        .traceParent(traceParent)
                        .build();

                saveToOutbox("Shipment", event.getOrderId(), "DeliveryFailed", failedEvent, traceParent);
            }
        } finally {
            TraceUtils.clearTraceContext();
        }
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
            log.error("Error serializando payload Outbox en Dispatch para {}", aggregateId, e);
            throw new RuntimeException("Fallo al serializar evento outbox en Dispatch", e);
        }
    }
}
