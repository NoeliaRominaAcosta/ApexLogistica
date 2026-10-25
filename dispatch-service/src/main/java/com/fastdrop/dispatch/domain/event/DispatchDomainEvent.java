package com.fastdrop.dispatch.domain.event;

import java.time.Instant;

public sealed interface DispatchDomainEvent
        permits DeliveryScheduledDomainEvent, DeliveryFailedDomainEvent,
                ShipmentPickedUpDomainEvent, ShipmentDeliveredDomainEvent, ShipmentCancelledDomainEvent {

    String orderId();
    Instant occurredAt();
}
