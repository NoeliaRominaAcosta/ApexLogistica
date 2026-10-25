package com.fastdrop.tracking.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.tracking.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingKafkaEventListener {

    private final TrackingService trackingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
            "${app.kafka.topics.inventory-events:warehouse.inventory-events}",
            "${app.kafka.topics.dispatch-events:logistics.dispatch-events}"
    }, groupId = "tracking-service-group")
    public void onDomainEvent(ConsumerRecord<String, String> record) {
        try {
            org.apache.kafka.common.header.Header traceHeader = record.headers().lastHeader(TraceUtils.TRACEPARENT_HEADER);
            if (traceHeader != null) {
                TraceUtils.setTraceContext(new String(traceHeader.value(), StandardCharsets.UTF_8));
            }

            org.apache.kafka.common.header.Header eventTypeHeader = record.headers().lastHeader("eventType");
            String eventType = (eventTypeHeader != null) ? new String(eventTypeHeader.value(), StandardCharsets.UTF_8) : "";

            JsonNode rootNode = objectMapper.readTree(record.value());
            if (eventType.isBlank() && rootNode.has("eventType")) {
                eventType = rootNode.get("eventType").asText();
            }

            String orderId = rootNode.has("orderId") ? rootNode.get("orderId").asText() : record.key();
            log.info("Tracking Service procesando evento: {} para Orden={}", eventType, orderId);

            switch (eventType) {
                case "StockReserved":
                    String whId = rootNode.has("warehouseId") ? rootNode.get("warehouseId").asText() : null;
                    String addr = rootNode.has("deliveryAddress") ? rootNode.get("deliveryAddress").asText() : null;
                    trackingService.recordCheckpoint(orderId, "STOCK_RESERVED", "Stock reservado en depósito central",
                            whId, null, null, addr);
                    break;

                case "StockReservationRejected":
                    String rejReason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Stock insuficiente";
                    trackingService.recordCheckpoint(orderId, "STOCK_REJECTED", "Reserva rechazada: " + rejReason,
                            null, null, null, null);
                    break;

                case "DeliveryScheduled":
                    String drvId = rootNode.has("driverId") ? rootNode.get("driverId").asText() : null;
                    String trkNum = rootNode.has("trackingNumber") ? rootNode.get("trackingNumber").asText() : null;
                    trackingService.recordCheckpoint(orderId, "DELIVERY_SCHEDULED", "Conductor asignado y ruta planificada",
                            null, drvId, trkNum, null);
                    break;

                case "ShipmentPickedUp":
                    String pDrv = rootNode.has("driverId") ? rootNode.get("driverId").asText() : null;
                    String pTrk = rootNode.has("trackingNumber") ? rootNode.get("trackingNumber").asText() : null;
                    trackingService.recordCheckpoint(orderId, "IN_TRANSIT", "Envío retirado y en tránsito",
                            null, pDrv, pTrk, null);
                    break;

                case "ShipmentDelivered":
                    String dDrv = rootNode.has("driverId") ? rootNode.get("driverId").asText() : null;
                    String dTrk = rootNode.has("trackingNumber") ? rootNode.get("trackingNumber").asText() : null;
                    trackingService.recordCheckpoint(orderId, "DELIVERED", "Envío entregado exitosamente al destinatario",
                            null, dDrv, dTrk, null);
                    break;

                case "DeliveryFailed":
                    String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Fallo de despacho";
                    trackingService.recordCheckpoint(orderId, "DELIVERY_FAILED", "Despacho fallido: " + reason,
                            null, null, null, null);
                    break;

                case "ShipmentCancelled":
                    String shpCancelReason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Envío cancelado";
                    trackingService.recordCheckpoint(orderId, "CANCELLED", "Envío cancelado: " + shpCancelReason,
                            null, null, null, null);
                    break;

                case "StockCancelled":
                    String cancelReason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Saga revertida";
                    trackingService.recordCheckpoint(orderId, "CANCELLED", "Orden cancelada y stock devuelto: " + cancelReason,
                            null, null, null, null);
                    break;

                default:
                    log.debug("Evento no mapeado en Tracking: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error al procesar evento en Tracking Service", e);
        } finally {
            TraceUtils.clearTraceContext();
        }
    }
}
