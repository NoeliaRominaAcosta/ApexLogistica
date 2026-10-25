package com.fastdrop.dispatch.infrastructure.adapter.out.statemachine.action;

import com.fastdrop.dispatch.application.port.out.ShipmentContext;
import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.ssm.ContextAwareAction;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

@Component
public class DeliverShipmentAction extends ContextAwareAction<ShipmentState, ShipmentEvent, ShipmentContext> {

    @Override
    protected void run(ShipmentContext subject, StateContext<ShipmentState, ShipmentEvent> context) {
        subject.shipment().deliver();
    }
}
