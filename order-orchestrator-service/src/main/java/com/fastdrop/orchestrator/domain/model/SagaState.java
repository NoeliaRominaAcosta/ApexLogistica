package com.fastdrop.orchestrator.domain.model;

/**
 * Estados de la máquina de estados del proceso OrderSaga (Orquestador).
 */
public enum SagaState {
    /** Estado inicial en memoria antes de disparar el primer comando. */
    NEW,
    /** Comando ReserveStock emitido; esperando respuesta de warehouse-service. */
    RESERVING_STOCK,
    /** Stock reservado; comando ScheduleDelivery emitido a dispatch-service. */
    SCHEDULING_DELIVERY,
    /** Despacho programado y reserva confirmada; esperando retiro del conductor. */
    AWAITING_PICKUP,
    /** Envío en tránsito hacia el domicilio del cliente. */
    IN_TRANSIT,
    /** Cancelando envío en dispatch antes de liberar el stock en warehouse. */
    CANCELLING_SHIPMENT,
    /** Ejecutando compensación de liberación de stock en warehouse. */
    RELEASING_STOCK,
    /** Orden completada y entregada con éxito. */
    COMPLETED,
    /** Orden rechazada por falta de stock disponible. */
    REJECTED,
    /** Orden cancelada y stock devuelto al warehouse. */
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }

    public boolean requiresStepDeadline() {
        return this == RESERVING_STOCK
                || this == SCHEDULING_DELIVERY
                || this == CANCELLING_SHIPMENT
                || this == RELEASING_STOCK;
    }
}
