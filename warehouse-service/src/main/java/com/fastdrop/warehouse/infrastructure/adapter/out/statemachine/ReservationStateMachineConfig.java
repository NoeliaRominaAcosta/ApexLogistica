package com.fastdrop.warehouse.infrastructure.adapter.out.statemachine;

import com.fastdrop.statemachine.ssm.MetricsStateMachineListener;
import com.fastdrop.warehouse.domain.model.ReservationEvent;
import com.fastdrop.warehouse.domain.model.ReservationState;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.ConfirmReservationAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.RejectReservationAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.ReleaseStockAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.action.ReserveStockAction;
import com.fastdrop.warehouse.infrastructure.adapter.out.statemachine.guard.HasAvailableStockGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory(name = "reservationStateMachineFactory")
public class ReservationStateMachineConfig
        extends EnumStateMachineConfigurerAdapter<ReservationState, ReservationEvent> {

    private final HasAvailableStockGuard hasAvailableStockGuard;
    private final ReserveStockAction reserveStockAction;
    private final RejectReservationAction rejectReservationAction;
    private final ConfirmReservationAction confirmReservationAction;
    private final ReleaseStockAction releaseStockAction;
    private final MeterRegistry meterRegistry;

    public ReservationStateMachineConfig(
            HasAvailableStockGuard hasAvailableStockGuard,
            ReserveStockAction reserveStockAction,
            RejectReservationAction rejectReservationAction,
            ConfirmReservationAction confirmReservationAction,
            ReleaseStockAction releaseStockAction,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.hasAvailableStockGuard = hasAvailableStockGuard;
        this.reserveStockAction = reserveStockAction;
        this.rejectReservationAction = rejectReservationAction;
        this.confirmReservationAction = confirmReservationAction;
        this.releaseStockAction = releaseStockAction;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<ReservationState, ReservationEvent> config) throws Exception {
        config.withConfiguration()
                .autoStartup(false)
                .listener(new MetricsStateMachineListener<>("stock-reservation", meterRegistry));
    }

    @Override
    public void configure(StateMachineStateConfigurer<ReservationState, ReservationEvent> states) throws Exception {
        states.withStates()
                .initial(ReservationState.NEW)
                .choice(ReservationState.STOCK_CHECK)
                .states(EnumSet.allOf(ReservationState.class))
                .end(ReservationState.REJECTED)
                .end(ReservationState.CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ReservationState, ReservationEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(ReservationState.NEW).target(ReservationState.STOCK_CHECK)
                .event(ReservationEvent.RESERVE)
                .and()
                .withChoice()
                .source(ReservationState.STOCK_CHECK)
                .first(ReservationState.RESERVED, hasAvailableStockGuard, reserveStockAction)
                .last(ReservationState.REJECTED, rejectReservationAction)
                .and()
                .withExternal()
                .source(ReservationState.RESERVED).target(ReservationState.CONFIRMED)
                .event(ReservationEvent.CONFIRM)
                .action(confirmReservationAction)
                .and()
                .withExternal()
                .source(ReservationState.RESERVED).target(ReservationState.CANCELLED)
                .event(ReservationEvent.CANCEL)
                .action(releaseStockAction)
                .and()
                .withExternal()
                .source(ReservationState.CONFIRMED).target(ReservationState.CANCELLED)
                .event(ReservationEvent.CANCEL)
                .action(releaseStockAction);
    }
}
