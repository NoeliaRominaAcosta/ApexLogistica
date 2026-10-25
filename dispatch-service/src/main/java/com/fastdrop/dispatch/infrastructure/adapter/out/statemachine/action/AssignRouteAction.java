package com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.action;

import com.fastdrop.dispatch.application.port.out.ShipmentContext;
import com.fastdrop.dispatch.application.service.ShipmentService;
import com.fastdrop.dispatch.domain.model.RouteAssignment;
import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.ssm.ContextAwareAction;
import com.fastdrop.statemachine.ssm.StateContexts;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
public class AssignRouteAction extends ContextAwareAction<ShipmentState, ShipmentEvent, ShipmentContext> {

    @Override
    protected void run(ShipmentContext subject, StateContext<ShipmentState, ShipmentEvent> context) {
        RouteAssignment assignment = StateContexts.header(context, ShipmentService.HEADER_ROUTE_ASSIGNMENT);
        subject.shipment().assignRoute(assignment);
    }
}
