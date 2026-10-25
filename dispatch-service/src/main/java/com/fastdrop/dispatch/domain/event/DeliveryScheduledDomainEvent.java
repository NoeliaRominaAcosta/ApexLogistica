package com.fastdrop.dispatch.domain.event;

import java.time.Instant;

public record DeliveryScheduledDomainEvent(
        String shipmentId,
        String orderId,
        String trackingNumber,
        String driverId,
        String estimatedDeliveryTime,
        String shippingLabelUrl,
        Instant occurredAt
) implements DispatchDomainEvent {

    public DeliveryScheduledDomainEvent(String shipmentId, String orderId, String trackingNumber,
                                        String driverId, String estimatedDeliveryTime, String shippingLabelUrl) {
        this(shipmentId, orderId, trackingNumber, driverId, estimatedDeliveryTime, shippingLabelUrl, Instant.now());
    }
}
