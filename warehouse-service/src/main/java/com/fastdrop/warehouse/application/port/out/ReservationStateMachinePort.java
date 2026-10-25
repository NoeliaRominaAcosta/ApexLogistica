package com.fastdrop.warehouse.application.port.out;

import com.fastdrop.statemachine.api.StateMachinePort;
import com.fastdrop.warehouse.domain.model.ReservationEvent;
import com.fastdrop.warehouse.domain.model.ReservationState;

public interface ReservationStateMachinePort
        extends StateMachinePort<ReservationContext, ReservationState, ReservationEvent> {
}
