package com.fastdrop.dispatch.domain.event;

import java.time.Instant;

public record ShipmentCancelledDomainEvent(
        String shipmentId,
        String orderId,
        String reason,
        Instant occurredAt
) implements DispatchDomainEvent {

    public ShipmentCancelledDomainEvent(String shipmentId, String orderId, String reason) {
        this(shipmentId, orderId, reason, Instant.now());
    }
}
