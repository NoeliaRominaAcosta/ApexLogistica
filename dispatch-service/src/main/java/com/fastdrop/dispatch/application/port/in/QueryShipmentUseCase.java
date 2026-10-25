package com.fastdrop.dispatch.application.port.in;

import com.fastdrop.dispatch.domain.model.Shipment;

import java.util.Optional;

public interface QueryShipmentUseCase {
    Optional<Shipment> findByOrderId(String orderId);
}
