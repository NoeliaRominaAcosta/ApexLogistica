package com.fastdrop.warehouse.application.port.in;

import com.fastdrop.warehouse.application.port.in.command.ReserveStockCommand;
import com.fastdrop.warehouse.domain.model.StockReservation;

public interface ReserveStockUseCase {
    StockReservation reserve(ReserveStockCommand command);
}
