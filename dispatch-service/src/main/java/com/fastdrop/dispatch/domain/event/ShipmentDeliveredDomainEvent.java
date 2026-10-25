package com.fastdrop.dispatch.domain.event;

import java.time.Instant;

public record ShipmentDeliveredDomainEvent(
        String shipmentId,
        String orderId,
        String trackingNumber,
        String driverId,
        Instant occurredAt
) implements DispatchDomainEvent {

    public ShipmentDeliveredDomainEvent(String shipmentId, String orderId, String trackingNumber, String driverId) {
        this(shipmentId, orderId, trackingNumber, driverId, Instant.now());
    }
}
