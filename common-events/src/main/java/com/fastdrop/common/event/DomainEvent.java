package com.fastdrop.common.event;

import java.time.Instant;

public interface DomainEvent {
    String getEventId();
    String getAggregateId();
    String getEventType();
    Instant getOccurredAt();
    String getTraceParent();
}
