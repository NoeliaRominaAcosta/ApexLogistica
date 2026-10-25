package com.fastdrop.warehouse.application.port.out;

import com.fastdrop.warehouse.domain.model.StockReservation;

import java.util.Optional;

public interface ReservationRepositoryPort {
    Optional<StockReservation> findByOrderId(String orderId);
    StockReservation save(StockReservation reservation);
}
