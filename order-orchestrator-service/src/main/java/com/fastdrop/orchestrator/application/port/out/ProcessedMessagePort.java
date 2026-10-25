package com.fastdrop.orchestrator.application.port.out;

public interface ProcessedMessagePort {
    boolean isAlreadyProcessed(String eventId);
    void markProcessed(String eventId, String orderId, String eventType);
}
