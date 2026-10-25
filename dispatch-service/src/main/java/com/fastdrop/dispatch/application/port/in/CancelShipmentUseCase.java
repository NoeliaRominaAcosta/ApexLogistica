package com.fastdrop.dispatch.application.port.in;

import com.fastdrop.dispatch.application.port.in.command.CancelShipmentCommand;
import com.fastdrop.dispatch.domain.model.Shipment;

public interface CancelShipmentUseCase {
    Shipment cancel(CancelShipmentCommand command);
}
