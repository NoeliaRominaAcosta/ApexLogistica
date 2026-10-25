package com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.action;

import com.fastdrop.dispatch.application.port.out.ShipmentContext;
import com.fastdrop.dispatch.application.service.ShipmentService;
import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.ssm.ContextAwareAction;
import com.fastdrop.statemachine.ssm.StateContexts;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
public class PickUpShipmentAction extends ContextAwareAction<ShipmentState, ShipmentEvent, ShipmentContext> {

    @Override
    protected void run(ShipmentContext subject, StateContext<ShipmentState, ShipmentEvent> context) {
        String driverId = StateContexts.headerOrDefault(context, ShipmentService.HEADER_DRIVER_ID, subject.shipment().getDriverId());
        subject.shipment().pickUp(driverId);
    }
}
