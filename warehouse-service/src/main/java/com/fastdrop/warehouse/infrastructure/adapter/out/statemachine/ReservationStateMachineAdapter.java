package com.fastdrop.warehouse.infrastructure.adapter.out.statemachine;

import com.fastdrop.statemachine.ssm.AbstractStateMachineAdapter;
import com.fastdrop.warehouse.application.port.out.ReservationContext;
import com.fastdrop.warehouse.application.port.out.ReservationStateMachinePort;
import com.fastdrop.warehouse.domain.model.ReservationEvent;
import com.fastdrop.warehouse.domain.model.ReservationState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;

@Component
public class ReservationStateMachineAdapter
        extends AbstractStateMachineAdapter<ReservationContext, ReservationState, ReservationEvent>
        implements ReservationStateMachinePort {

    public ReservationStateMachineAdapter(
            @Qualifier("reservationStateMachineFactory") StateMachineFactory<ReservationState, ReservationEvent> factory) {
        super(factory);
    }
}
