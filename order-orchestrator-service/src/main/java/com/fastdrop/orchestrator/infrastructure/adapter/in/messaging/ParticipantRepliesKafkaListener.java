package com.fastdrop.orchestrator.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.orchestrator.application.port.in.HandleParticipantReplyUseCase;
import com.fastdrop.orchestrator.application.port.in.command.ParticipantReplyCommand;
import com.fastdrop.orchestrator.application.service.OrderSagaService;
import com.fastdrop.orchestrator.domain.model.SagaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantRepliesKafkaListener {

    private final HandleParticipantReplyUseCase handleParticipantReplyUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-events:warehouse.inventory-events}",
            groupId = "orchestrator-inventory-replies-group"
    )
    public void onInventoryEvent(ConsumerRecord<String, String> record) {
        processReply(record);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.dispatch-events:logistics.dispatch-events}",
            groupId = "orchestrator-dispatch-replies-group"
    )
    public void onDispatchEvent(ConsumerRecord<String, String> record) {
        processReply(record);
    }

    private void processReply(ConsumerRecord<String, String> record) {
        try {
            org.apache.kafka.common.header.Header traceHeader = record.headers().lastHeader(TraceUtils.TRACEPARENT_HEADER);
            if (traceHeader != null) {
                TraceUtils.setTraceContext(new String(traceHeader.value(), StandardCharsets.UTF_8));
            }

            JsonNode rootNode = objectMapper.readTree(record.value());
            String eventType = extractEventType(record, rootNode);
            String eventId = rootNode.path("eventId").asText("");
            String orderId = rootNode.has("orderId") ? rootNode.get("orderId").asText() : record.key();

            SagaEvent sagaEvent = mapToSagaEvent(eventType);
            if (sagaEvent == null) {
                return;
            }

            Map<String, Object> metadata = new HashMap<>();
            if (rootNode.has("warehouseId")) {
                metadata.put(OrderSagaService.HEADER_WAREHOUSE_ID, rootNode.get("warehouseId").asText());
            }
            if (rootNode.has("shipmentId")) {
                metadata.put(OrderSagaService.HEADER_SHIPMENT_ID, rootNode.get("shipmentId").asText());
            }
            if (rootNode.has("trackingNumber")) {
                metadata.put(OrderSagaService.HEADER_TRACKING_NUMBER, rootNode.get("trackingNumber").asText());
            }
            if (rootNode.has("driverId")) {
                metadata.put(OrderSagaService.HEADER_DRIVER_ID, rootNode.get("driverId").asText());
            }
            if (rootNode.has("reason")) {
                metadata.put(OrderSagaService.HEADER_REASON, rootNode.get("reason").asText());
            }

            handleParticipantReplyUseCase.onParticipantReply(
                    new ParticipantReplyCommand(eventId, orderId, sagaEvent, metadata)
            );
        } catch (Exception e) {
            log.error("Error procesando respuesta de participante en Orquestador: key={}", record.key(), e);
        } finally {
            TraceUtils.clearTraceContext();
        }
    }

    private SagaEvent mapToSagaEvent(String eventType) {
        return switch (eventType) {
            case "StockReserved" -> SagaEvent.STOCK_RESERVED;
            case "StockReservationRejected" -> SagaEvent.STOCK_REJECTED;
            case "StockCancelled" -> SagaEvent.STOCK_RELEASED;
            case "DeliveryScheduled" -> SagaEvent.DELIVERY_SCHEDULED;
            case "DeliveryFailed" -> SagaEvent.DELIVERY_FAILED;
            case "ShipmentPickedUp" -> SagaEvent.SHIPMENT_PICKED_UP;
            case "ShipmentDelivered" -> SagaEvent.SHIPMENT_DELIVERED;
            case "ShipmentCancelled" -> SagaEvent.SHIPMENT_CANCELLED;
            default -> null;
        };
    }

    private String extractEventType(ConsumerRecord<String, String> record, JsonNode rootNode) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader("eventType");
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return rootNode.has("eventType") ? rootNode.get("eventType").asText() : "";
    }
}
