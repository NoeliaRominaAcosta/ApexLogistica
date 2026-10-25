package com.fastdrop.dispatch.domain.model;

/**
 * Estados del ciclo de vida de un despacho (Shipment).
 */
public enum ShipmentState {
    /** Envío creado, pendiente de cálculo de ruta y asignación de conductor. */
    PENDING_ROUTING,
    /** Ruta calculada y conductor asignado; esperando retiro físico. */
    SCHEDULED,
    /** Retirado por el conductor; en tránsito hacia el domicilio de entrega. */
    IN_TRANSIT,
    /** Entregado con éxito al destinatario. */
    DELIVERED,
    /** Falló la asignación de ruta o la entrega en domicilio. */
    FAILED,
    /** Envío cancelado antes de completarse. */
    CANCELLED;

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == CANCELLED;
    }
}
