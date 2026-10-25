package com.fastdrop.dispatch.application.port.in;

import com.fastdrop.dispatch.application.port.in.command.DeliverShipmentCommand;
import com.fastdrop.dispatch.domain.model.Shipment;

public interface DeliverShipmentUseCase {
    Shipment deliver(DeliverShipmentCommand command);
}
