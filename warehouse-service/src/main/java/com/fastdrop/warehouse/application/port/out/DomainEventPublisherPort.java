package com.fastdrop.warehouse.application.port.out;

import com.fastdrop.warehouse.domain.event.WarehouseDomainEvent;

import java.util.List;

public interface DomainEventPublisherPort {
    void publish(List<WarehouseDomainEvent> events);
}
