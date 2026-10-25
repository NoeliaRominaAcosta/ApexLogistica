package com.fastdrop.dispatch.application.port.out;

import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.api.StateMachinePort;

public interface ShipmentStateMachinePort
        extends StateMachinePort<ShipmentContext, ShipmentState, ShipmentEvent> {
}
