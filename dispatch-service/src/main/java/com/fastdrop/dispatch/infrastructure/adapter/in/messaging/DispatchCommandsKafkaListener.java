package com.fastdrop.dispatch.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.dispatch.application.port.in.CancelShipmentUseCase;
import com.fastdrop.dispatch.application.port.in.ScheduleShipmentUseCase;
import com.fastdrop.dispatch.application.port.in.command.CancelShipmentCommand;
import com.fastdrop.dispatch.application.port.in.command.ScheduleShipmentCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchCommandsKafkaListener {

    private final ScheduleShipmentUseCase scheduleShipmentUseCase;
    private final CancelShipmentUseCase cancelShipmentUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.dispatch-commands:dispatch.commands}",
            groupId = "dispatch-commands-group"
    )
    public void onDispatchCommand(ConsumerRecord<String, String> record) {
        processRecord(record);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-events:warehouse.inventory-events}",
            groupId = "dispatch-service-group"
    )
    public void onInventoryEvent(ConsumerRecord<String, String> record) {
        processRecord(record);
    }

    private void processRecord(ConsumerRecord<String, String> record) {
        try {
            org.apache.kafka.common.header.Header traceHeader = record.headers().lastHeader(TraceUtils.TRACEPARENT_HEADER);
            if (traceHeader != null) {
                TraceUtils.setTraceContext(new String(traceHeader.value(), StandardCharsets.UTF_8));
            }

            JsonNode rootNode = objectMapper.readTree(record.value());
            String eventType = extractEventType(record, rootNode);
            String orderId = rootNode.has("orderId") ? rootNode.get("orderId").asText() : record.key();

            log.info("Dispatch Service recibió mensaje key={}, eventType={}", record.key(), eventType);

            if ("ScheduleDelivery".equalsIgnoreCase(eventType) || "StockReserved".equalsIgnoreCase(eventType)) {
                String warehouseId = rootNode.path("warehouseId").asText("WH-CENTRAL-01");
                String deliveryAddress = rootNode.path("deliveryAddress").asText("");
                scheduleShipmentUseCase.schedule(new ScheduleShipmentCommand(orderId, warehouseId, deliveryAddress));
            } else if ("CancelShipment".equalsIgnoreCase(eventType) || "StockCancelled".equalsIgnoreCase(eventType)) {
                String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Cancelación solicitada";
                cancelShipmentUseCase.cancel(new CancelShipmentCommand(orderId, reason));
            }
        } catch (Exception e) {
            log.error("Error al procesar mensaje en Dispatch Service: key={}", record.key(), e);
        } finally {
            TraceUtils.clearTraceContext();
        }
    }

    private String extractEventType(ConsumerRecord<String, String> record, JsonNode rootNode) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader("eventType");
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return rootNode.has("eventType") ? rootNode.get("eventType").asText() : "";
    }
}
