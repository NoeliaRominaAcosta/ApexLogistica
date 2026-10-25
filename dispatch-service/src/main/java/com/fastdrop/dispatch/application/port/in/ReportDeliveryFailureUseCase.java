package com.fastdrop.dispatch.application.port.in;

import com.fastdrop.dispatch.application.port.in.command.ReportDeliveryFailureCommand;
import com.fastdrop.dispatch.domain.model.Shipment;

public interface ReportDeliveryFailureUseCase {
    Shipment reportFailure(ReportDeliveryFailureCommand command);
}
