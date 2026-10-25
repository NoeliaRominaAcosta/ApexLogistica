package com.fastdrop.orchestrator.domain.model;

/**
 * Eventos (triggers) que avanzan o compensan la máquina de estados OrderSaga.
 */
public enum SagaEvent {
    START,
    STOCK_RESERVED,
    STOCK_REJECTED,
    DELIVERY_SCHEDULED,
    DELIVERY_FAILED,
    SHIPMENT_PICKED_UP,
    SHIPMENT_DELIVERED,
    SHIPMENT_CANCELLED,
    STOCK_RELEASED,
    CANCEL_REQUESTED,
    TIMEOUT
}
