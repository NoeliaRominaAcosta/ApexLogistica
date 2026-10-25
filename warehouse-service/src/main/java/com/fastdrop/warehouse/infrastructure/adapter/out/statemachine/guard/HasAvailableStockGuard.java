package com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.guard;

import com.fastdrop.statemachine.ssm.ContextAwareGuard;
import com.fastdrop.warehouse.application.port.out.ReservationContext;
import com.fastdrop.warehouse.domain.model.ReservationEvent;
import com.fastdrop.warehouse.domain.model.ReservationState;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
public class HasAvailableStockGuard extends ContextAwareGuard<ReservationState, ReservationEvent, ReservationContext> {

    @Override
    protected boolean test(ReservationContext subject, StateContext<ReservationState, ReservationEvent> context) {
        return subject.inventory() != null
                && subject.inventory().hasAvailableStock(subject.reservation().getQuantity());
    }
}
