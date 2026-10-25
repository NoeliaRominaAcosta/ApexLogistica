package com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action;

import com.fastdrop.statemachine.ssm.ContextAwareAction;
import com.fastdrop.warehouse.application.port.out.ReservationContext;
import com.fastdrop.warehouse.domain.model.ReservationEvent;
import com.fastdrop.warehouse.domain.model.ReservationState;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
public class ReserveStockAction extends ContextAwareAction<ReservationState, ReservationEvent, ReservationContext> {

    @Override
    protected void run(ReservationContext subject, StateContext<ReservationState, ReservationEvent> context) {
        subject.reservation().reserve(subject.inventory());
    }
}
