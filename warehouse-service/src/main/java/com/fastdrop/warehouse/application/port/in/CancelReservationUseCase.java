package com.fastdrop.warehouse.application.port.in;

import com.fastdrop.warehouse.application.port.in.command.CancelReservationCommand;
import com.fastdrop.warehouse.domain.model.StockReservation;

public interface CancelReservationUseCase {
    StockReservation cancel(CancelReservationCommand command);
}
