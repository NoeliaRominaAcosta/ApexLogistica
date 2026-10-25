package com.fastdrop.dispatch.application.port.out;

import com.fastdrop.dispatch.domain.event.DispatchDomainEvent;

import java.util.List;

public interface DomainEventPublisherPort {
    void publish(List<DispatchDomainEvent> events);
}
