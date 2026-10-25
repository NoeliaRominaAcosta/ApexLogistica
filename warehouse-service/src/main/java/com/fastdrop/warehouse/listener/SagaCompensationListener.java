package com.fastdrop.warehouse.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaCompensationListener {

    private final WarehouseService warehouseService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.dispatch-events:logistics.dispatch-events}",
                   groupId = "warehouse-compensation-group")
    public void onDispatchEvent(ConsumerRecord<String, String> record) {
        processRecord(record);
    }

    @KafkaListener(topics = "${app.kafka.topics.saga-commands:orders.saga-commands}",
                   groupId = "warehouse-commands-group")
    public void onSagaCommand(ConsumerRecord<String, String> record) {
        processRecord(record);
    }

    private void processRecord(ConsumerRecord<String, String> record) {
        try {
            // Propagar trace context desde headers
            org.apache.kafka.common.header.Header traceHeader = record.headers().lastHeader(TraceUtils.TRACEPARENT_HEADER);
            if (traceHeader != null) {
                TraceUtils.setTraceContext(new String(traceHeader.value(), StandardCharsets.UTF_8));
            }

            String payload = record.value();
            JsonNode rootNode = objectMapper.readTree(payload);

            String eventType = "";
            org.apache.kafka.common.header.Header eventTypeHeader = record.headers().lastHeader("eventType");
            if (eventTypeHeader != null) {
                eventType = new String(eventTypeHeader.value(), StandardCharsets.UTF_8);
            } else if (rootNode.has("eventType")) {
                eventType = rootNode.get("eventType").asText();
            }

            log.info("Recibido mensaje en Warehouse: key={}, eventType={}", record.key(), eventType);

            if ("DeliveryFailed".equalsIgnoreCase(eventType)) {
                String orderId = rootNode.has("orderId") ? rootNode.get("orderId").asText() : record.key();
                String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Fallo en despacho reportado";
                warehouseService.cancelReservation(orderId, reason);
            } else if ("CancelStockReservation".equalsIgnoreCase(eventType)) {
                String orderId = rootNode.has("orderId") ? rootNode.get("orderId").asText() : record.key();
                String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Comando de cancelación de Saga";
                warehouseService.cancelReservation(orderId, reason);
            }
        } catch (Exception e) {
            log.error("Error al procesar mensaje de compensación en Warehouse", e);
        } finally {
            TraceUtils.clearTraceContext();
        }
    }
}
