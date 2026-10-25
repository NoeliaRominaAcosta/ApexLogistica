package com.fastdrop.warehouse.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.warehouse.application.port.in.CancelReservationUseCase;
import com.fastdrop.warehouse.application.port.in.ConfirmReservationUseCase;
import com.fastdrop.warehouse.application.port.in.ReserveStockUseCase;
import com.fastdrop.warehouse.application.port.in.command.CancelReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ConfirmReservationCommand;
import com.fastdrop.warehouse.application.port.in.command.ReserveStockCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseCommandsKafkaListener {

    private final ReserveStockUseCase reserveStockUseCase;
    private final ConfirmReservationUseCase confirmReservationUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.warehouse-commands:warehouse.commands}",
            groupId = "warehouse-commands-group"
    )
    public void onWarehouseCommand(ConsumerRecord<String, String> record) {
        processRecord(record);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.saga-commands:orders.saga-commands}",
            groupId = "warehouse-saga-commands-group"
    )
    public void onLegacySagaCommand(ConsumerRecord<String, String> record) {
        processRecord(record);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.dispatch-events:logistics.dispatch-events}",
            groupId = "warehouse-compensation-group"
    )
    public void onDispatchEvent(ConsumerRecord<String, String> record) {
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

            log.info("Recibido mensaje en Warehouse: key={}, eventType={}", record.key(), eventType);

            if ("ReserveStock".equalsIgnoreCase(eventType)) {
                String sku = rootNode.path("sku").asText();
                int quantity = rootNode.path("quantity").asInt(1);
                String warehouseId = rootNode.path("warehouseId").asText(null);
                String deliveryAddress = rootNode.path("deliveryAddress").asText("");
                reserveStockUseCase.reserve(new ReserveStockCommand(orderId, sku, quantity, warehouseId, deliveryAddress));
            } else if ("ConfirmReservation".equalsIgnoreCase(eventType) || "DeliveryScheduled".equalsIgnoreCase(eventType)) {
                String trackingNumber = rootNode.path("trackingNumber").asText("");
                confirmReservationUseCase.confirm(new ConfirmReservationCommand(orderId, trackingNumber));
            } else if ("ReleaseStock".equalsIgnoreCase(eventType)
                    || "CancelStockReservation".equalsIgnoreCase(eventType)
                    || "DeliveryFailed".equalsIgnoreCase(eventType)) {
                String reason = rootNode.has("reason") ? rootNode.get("reason").asText() : "Compensación de Saga";
                cancelReservationUseCase.cancel(new CancelReservationCommand(orderId, reason));
            }
        } catch (Exception e) {
            log.error("Error procesando comando/evento en Warehouse: key={}", record.key(), e);
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
