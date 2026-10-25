package com.fastdrop.dispatch.application.port.in;

import com.fastdrop.dispatch.application.port.in.command.PickUpShipmentCommand;
import com.fastdrop.dispatch.domain.model.Shipment;

public interface PickUpShipmentUseCase {
    Shipment pickUp(PickUpShipmentCommand command);
}
