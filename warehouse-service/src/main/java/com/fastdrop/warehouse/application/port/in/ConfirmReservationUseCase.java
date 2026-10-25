package com.fastdrop.warehouse.application.port.in;

import com.fastdrop.warehouse.application.port.in.command.ConfirmReservationCommand;
import com.fastdrop.warehouse.domain.model.StockReservation;

public interface ConfirmReservationUseCase {
    StockReservation confirm(ConfirmReservationCommand command);
}
