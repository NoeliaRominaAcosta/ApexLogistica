package com.fastdrop.orchestrator.domain.command;

import java.time.Instant;

/**
 * Comando saliente solicitado por una transición de la saga hacia un microservicio participante.
 */
public sealed interface SagaOutboundCommand
        permits ReserveStockSagaCommand, ConfirmReservationSagaCommand, ReleaseStockSagaCommand,
                ScheduleDeliverySagaCommand, CancelShipmentSagaCommand {

    String orderId();
    Instant occurredAt();
}
