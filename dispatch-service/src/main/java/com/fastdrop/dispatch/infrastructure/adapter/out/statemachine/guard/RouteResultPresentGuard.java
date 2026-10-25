package com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.guard;

import com.fastdrop.dispatch.application.port.out.ShipmentContext;
import com.fastdrop.dispatch.application.service.ShipmentService;
import com.fastdrop.dispatch.domain.model.RouteAssignment;
import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.ssm.ContextAwareGuard;
import com.fastdrop.statemachine.ssm.StateContexts;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
public class RouteResultPresentGuard extends ContextAwareGuard<ShipmentState, ShipmentEvent, ShipmentContext> {

    @Override
    protected boolean test(ShipmentContext subject, StateContext<ShipmentState, ShipmentEvent> context) {
        RouteAssignment assignment = StateContexts.header(context, ShipmentService.HEADER_ROUTE_ASSIGNMENT);
        return assignment != null
                && assignment.driverId() != null && !assignment.driverId().isBlank()
                && assignment.trackingNumber() != null && !assignment.trackingNumber().isBlank();
    }
}
