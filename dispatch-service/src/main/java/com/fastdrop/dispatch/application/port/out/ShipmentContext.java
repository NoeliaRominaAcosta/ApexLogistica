package com.fastdrop.dispatch.application.port.out;

import com.fastdrop.dispatch.domain.model.Shipment;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.api.TransitionSubject;

public record ShipmentContext(Shipment shipment) implements TransitionSubject<ShipmentState> {

    @Override
    public String machineId() {
        return shipment.getOrderId();
    }

    @Override
    public ShipmentState currentState() {
        return shipment.getState();
    }
}
