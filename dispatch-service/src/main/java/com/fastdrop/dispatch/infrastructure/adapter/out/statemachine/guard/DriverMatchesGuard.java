package com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.guard;

import com.fastdrop.dispatch.application.port.out.ShipmentContext;
import com.fastdrop.dispatch.application.service.ShipmentService;
import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.ssm.ContextAwareGuard;
import com.fastdrop.statemachine.ssm.StateContexts;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
public class DriverMatchesGuard extends ContextAwareGuard<ShipmentState, ShipmentEvent, ShipmentContext> {

    @Override
    protected boolean test(ShipmentContext subject, StateContext<ShipmentState, ShipmentEvent> context) {
        String requestedDriver = StateContexts.headerOrDefault(context, ShipmentService.HEADER_DRIVER_ID, "");
        String assignedDriver = subject.shipment().getDriverId();
        return requestedDriver.isBlank() || assignedDriver == null || assignedDriver.equals(requestedDriver);
    }
}
