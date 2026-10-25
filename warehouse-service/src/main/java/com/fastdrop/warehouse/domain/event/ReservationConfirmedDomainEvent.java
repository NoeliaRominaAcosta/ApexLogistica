package com.fastdrop.warehouse.domain.event;

import java.time.Instant;

public record ReservationConfirmedDomainEvent(
        String orderId,
        String trackingNumber,
        Instant occurredAt
) implements WarehouseDomainEvent {

    public ReservationConfirmedDomainEvent(String orderId, String trackingNumber) {
        this(orderId, trackingNumber, Instant.now());
    }
}
