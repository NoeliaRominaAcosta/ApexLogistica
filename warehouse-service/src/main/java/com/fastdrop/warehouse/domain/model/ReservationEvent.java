package com.fastdrop.warehouse.domain.model;

/**
 * Eventos (triggers) que disparan transiciones en la máquina de estados de una reserva de stock.
 */
public enum ReservationEvent {
    RESERVE,
    CONFIRM,
    CANCEL
}
