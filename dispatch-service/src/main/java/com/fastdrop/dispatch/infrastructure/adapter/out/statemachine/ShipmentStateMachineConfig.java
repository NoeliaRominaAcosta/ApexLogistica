package com.fastdrop.dispatch.infrastructure.adapter.out.statemachine;

import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.action.*;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.guard.DriverMatchesGuard;
import com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.guard.RouteResultPresentGuard;
import com.fastdrop.statemachine.ssm.MetricsStateMachineListener;
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
@EnableStateMachineFactory(name = "shipmentStateMachineFactory")
public class ShipmentStateMachineConfig
        extends EnumStateMachineConfigurerAdapter<ShipmentState, ShipmentEvent> {

    private final RouteResultPresentGuard routeResultPresentGuard;
    private final DriverMatchesGuard driverMatchesGuard;
    private final AssignRouteAction assignRouteAction;
    private final MarkShipmentFailedAction markShipmentFailedAction;
    private final PickUpShipmentAction pickUpShipmentAction;
    private final DeliverShipmentAction deliverShipmentAction;
    private final CancelShipmentAction cancelShipmentAction;
    private final MeterRegistry meterRegistry;

    public ShipmentStateMachineConfig(
            RouteResultPresentGuard routeResultPresentGuard,
            DriverMatchesGuard driverMatchesGuard,
            AssignRouteAction assignRouteAction,
            MarkShipmentFailedAction markShipmentFailedAction,
            PickUpShipmentAction pickUpShipmentAction,
            DeliverShipmentAction deliverShipmentAction,
            CancelShipmentAction cancelShipmentAction,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.routeResultPresentGuard = routeResultPresentGuard;
        this.driverMatchesGuard = driverMatchesGuard;
        this.assignRouteAction = assignRouteAction;
        this.markShipmentFailedAction = markShipmentFailedAction;
        this.pickUpShipmentAction = pickUpShipmentAction;
        this.deliverShipmentAction = deliverShipmentAction;
        this.cancelShipmentAction = cancelShipmentAction;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<ShipmentState, ShipmentEvent> config) throws Exception {
        config.withConfiguration()
                .autoStartup(false)
                .listener(new MetricsStateMachineListener<>("shipment", meterRegistry));
    }

    @Override
    public void configure(StateMachineStateConfigurer<ShipmentState, ShipmentEvent> states) throws Exception {
        states.withStates()
                .initial(ShipmentState.PENDING_ROUTING)
                .states(EnumSet.allOf(ShipmentState.class))
                .end(ShipmentState.DELIVERED)
                .end(ShipmentState.FAILED)
                .end(ShipmentState.CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ShipmentState, ShipmentEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(ShipmentState.PENDING_ROUTING).target(ShipmentState.SCHEDULED)
                .event(ShipmentEvent.ROUTE_ASSIGNED)
                .guard(routeResultPresentGuard)
                .action(assignRouteAction)
                .and()
                .withExternal()
                .source(ShipmentState.PENDING_ROUTING).target(ShipmentState.FAILED)
                .event(ShipmentEvent.ROUTE_FAILED)
                .action(markShipmentFailedAction)
                .and()
                .withExternal()
                .source(ShipmentState.PENDING_ROUTING).target(ShipmentState.CANCELLED)
                .event(ShipmentEvent.CANCEL)
                .action(cancelShipmentAction)
                .and()
                .withExternal()
                .source(ShipmentState.SCHEDULED).target(ShipmentState.IN_TRANSIT)
                .event(ShipmentEvent.PICK_UP)
                .guard(driverMatchesGuard)
                .action(pickUpShipmentAction)
                .and()
                .withExternal()
                .source(ShipmentState.SCHEDULED).target(ShipmentState.CANCELLED)
                .event(ShipmentEvent.CANCEL)
                .action(cancelShipmentAction)
                .and()
                .withExternal()
                .source(ShipmentState.IN_TRANSIT).target(ShipmentState.DELIVERED)
                .event(ShipmentEvent.DELIVER)
                .action(deliverShipmentAction)
                .and()
                .withExternal()
                .source(ShipmentState.IN_TRANSIT).target(ShipmentState.FAILED)
                .event(ShipmentEvent.DELIVERY_FAILED)
                .action(markShipmentFailedAction);
    }
}
