package com.fastdrop.dispatch.domain.model;

/**
 * Eventos (triggers) que disparan transiciones en la máquina de estados de un envío.
 */
public enum ShipmentEvent {
    ROUTE_ASSIGNED,
    ROUTE_FAILED,
    PICK_UP,
    DELIVER,
    DELIVERY_FAILED,
    CANCEL
}
