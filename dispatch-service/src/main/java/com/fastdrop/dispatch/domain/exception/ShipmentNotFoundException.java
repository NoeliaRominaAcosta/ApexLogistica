package com.fastdrop.dispatch.domain.exception;

public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(String orderId) {
        super("No existe despacho para la orden: " + orderId);
    }
}
