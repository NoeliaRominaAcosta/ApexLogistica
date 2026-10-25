package com.fastdrop.orchestrator.domain.exception;

public class OrderSagaNotFoundException extends RuntimeException {

    public OrderSagaNotFoundException(String orderId) {
        super("No existe saga para la orden: " + orderId);
    }
}
