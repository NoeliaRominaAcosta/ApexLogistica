package com.fastdrop.warehouse.domain.model;

/**
 * Estados del ciclo de vida de una reserva de stock en el almacén.
 */
public enum ReservationState {
    /** Estado inicial en memoria antes de evaluar disponibilidad de stock. */
    NEW,
    /** Pseudo-estado de elección (choice) para evaluar stock disponible. */
    STOCK_CHECK,
    /** Stock reservado físicamente, pendiente de confirmación de despacho. */
    RESERVED,
    /** Reserva confirmada tras asignación exitosa de despacho/conductor. */
    CONFIRMED,
    /** Reserva rechazada por falta de stock disponible. */
    REJECTED,
    /** Reserva cancelada y stock devuelto al inventario disponible. */
    CANCELLED;

    public boolean isTerminal() {
        return this == REJECTED || this == CANCELLED;
    }
}
