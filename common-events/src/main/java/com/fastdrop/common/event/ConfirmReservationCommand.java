package com.fastdrop.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmReservationCommand implements DomainEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String orderId;
    private String trackingNumber;
    @Builder.Default
    private Instant occurredAt = Instant.now();
    private String traceParent;

    @Override
    public String getAggregateId() {
        return orderId;
    }

    @Override
    public String getEventType() {
        return "ConfirmReservation";
    }
}
