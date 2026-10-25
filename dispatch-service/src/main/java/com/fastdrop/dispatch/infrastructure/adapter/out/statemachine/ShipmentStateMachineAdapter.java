package com.fastdrop.dispatch.infrastructure.adapter.out.statemachine;

import com.fastdrop.dispatch.application.port.out.ShipmentContext;
import com.fastdrop.dispatch.application.port.out.ShipmentStateMachinePort;
import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.ssm.AbstractStateMachineAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;

@Component
public class ShipmentStateMachineAdapter
        extends AbstractStateMachineAdapter<ShipmentContext, ShipmentState, ShipmentEvent>
        implements ShipmentStateMachinePort {

    public ShipmentStateMachineAdapter(
            @Qualifier("shipmentStateMachineFactory") StateMachineFactory<ShipmentState, ShipmentEvent> factory) {
        super(factory);
    }
}
