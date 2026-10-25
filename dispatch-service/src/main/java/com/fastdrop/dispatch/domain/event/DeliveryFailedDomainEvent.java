package com.fastdrop.dispatch.domain.event;

import java.time.Instant;

public record DeliveryFailedDomainEvent(
        String orderId,
        String reason,
        Instant occurredAt
) implements DispatchDomainEvent {

    public DeliveryFailedDomainEvent(String orderId, String reason) {
        this(orderId, reason, Instant.now());
    }
}
