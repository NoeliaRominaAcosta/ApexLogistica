package com.fastdrop.dispatch.application.port.out;

import com.fastdrop.dispatch.domain.model.Shipment;
import com.fastdrop.dispatch.domain.model.ShipmentState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepositoryPort {
    Optional<Shipment> findByOrderId(String orderId);
    List<Shipment> findByStateAndCreatedAtBefore(ShipmentState state, Instant threshold);
    Shipment save(Shipment shipment);
}
