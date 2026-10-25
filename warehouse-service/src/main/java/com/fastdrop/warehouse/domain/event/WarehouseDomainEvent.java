package com.fastdrop.warehouse.domain.event;

import java.time.Instant;

/**
 * Evento de dominio interno del contexto de Warehouse.
 */
public sealed interface WarehouseDomainEvent
        permits StockReservedDomainEvent, StockReservationRejectedDomainEvent,
                ReservationConfirmedDomainEvent, StockReleasedDomainEvent {

    String orderId();
    Instant occurredAt();
}
