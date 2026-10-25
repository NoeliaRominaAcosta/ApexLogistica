package com.fastdrop.dispatch.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastdrop.common.event.StockReservedEvent;
import com.fastdrop.common.util.TraceUtils;
import com.fastdrop.dispatch.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReservedEventListener {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.inventory-events:warehouse.inventory-events}",
                   groupId = "dispatch-service-group")
    public void onInventoryEvent(ConsumerRecord<String, String> record) {
        try {
            org.apache.kafka.common.header.Header traceHeader = record.headers().lastHeader(TraceUtils.TRACEPARENT_HEADER);
            String traceParent = null;
            if (traceHeader != null) {
                traceParent = new String(traceHeader.value(), StandardCharsets.UTF_8);
                TraceUtils.setTraceContext(traceParent);
            }

            org.apache.kafka.common.header.Header eventTypeHeader = record.headers().lastHeader("eventType");
            String eventType = (eventTypeHeader != null) ? new String(eventTypeHeader.value(), StandardCharsets.UTF_8) : "";

            log.info("Dispatch Service recibió evento key={}, eventType={}", record.key(), eventType);

            if ("StockReserved".equalsIgnoreCase(eventType) || record.value().contains("\"StockReserved\"")) {
                StockReservedEvent event = objectMapper.readValue(record.value(), StockReservedEvent.class);
                if (traceParent != null && event.getTraceParent() == null) {
                    event.setTraceParent(traceParent);
                }
                dispatchService.processStockReserved(event);
            }
        } catch (Exception e) {
            log.error("Error al procesar mensaje de inventario en Dispatch Service", e);
        } finally {
            TraceUtils.clearTraceContext();
        }
    }
}
