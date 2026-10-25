package com.fastdrop.dispatch.application.port.in;

import com.fastdrop.dispatch.application.port.in.command.ScheduleShipmentCommand;
import com.fastdrop.dispatch.domain.model.Shipment;

public interface ScheduleShipmentUseCase {
    Shipment schedule(ScheduleShipmentCommand command);
    void retryPendingRouting(String orderId);
}
