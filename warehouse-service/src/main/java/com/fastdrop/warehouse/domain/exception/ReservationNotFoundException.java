package com.fastdrop.warehouse.domain.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String orderId) {
        super("No existe reserva de stock para la orden: " + orderId);
    }
}
