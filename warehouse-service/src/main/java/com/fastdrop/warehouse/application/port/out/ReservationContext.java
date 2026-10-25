package com.fastdrop.warehouse.application.port.out;

import com.fastdrop.statemachine.api.TransitionSubject;
import com.fastdrop.warehouse.domain.model.InventoryItem;
import com.fastdrop.warehouse.domain.model.ReservationState;
import com.fastdrop.warehouse.domain.model.StockReservation;

public record ReservationContext(StockReservation reservation, InventoryItem inventory)
        implements TransitionSubject<ReservationState> {

    @Override
    public String machineId() {
        return reservation.getOrderId();
    }

    @Override
    public ReservationState currentState() {
        return reservation.getState();
    }
}
