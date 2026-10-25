package com.fastdrop.dispatch.domain.event;

import java.time.Instant;

public record ShipmentPickedUpDomainEvent(
        String shipmentId,
        String orderId,
        String trackingNumber,
        String driverId,
        Instant occurredAt
) implements DispatchDomainEvent {

    public ShipmentPickedUpDomainEvent(String shipmentId, String orderId, String trackingNumber, String driverId) {
        this(shipmentId, orderId, trackingNumber, driverId, Instant.now());
    }
}
