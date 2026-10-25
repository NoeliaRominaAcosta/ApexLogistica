package com.fastdrop.dispatch.application.service;

import com.fastdrop.dispatch.application.port.in.*;
import com.fastdrop.dispatch.application.port.in.command.*;
import com.fastdrop.dispatch.application.port.out.*;
import com.fastdrop.dispatch.domain.exception.ShipmentNotFoundException;
import com.fastdrop.dispatch.domain.model.Shipment;
import com.fastdrop.dispatch.domain.model.ShipmentEvent;
import com.fastdrop.dispatch.domain.model.ShipmentState;
import com.fastdrop.statemachine.api.InvalidStateTransitionException;
import com.fastdrop.statemachine.api.TransitionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService implements
        ScheduleShipmentUseCase,
        PickUpShipmentUseCase,
        DeliverShipmentUseCase,
        ReportDeliveryFailureUseCase,
        CancelShipmentUseCase,
        QueryShipmentUseCase {

    public static final String HEADER_ROUTE_ASSIGNMENT = "routeAssignment";
    public static final String HEADER_REASON = "reason";
    public static final String HEADER_DRIVER_ID = "driverId";

    private final ShipmentRepositoryPort shipmentRepository;
    private final RoutingPort routingPort;
    private final ShipmentStateMachinePort stateMachine;
    private final DomainEventPublisherPort eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Shipment schedule(ScheduleShipmentCommand command) {
        log.info("Iniciando despacho para Orden={} desde depósito={}", command.orderId(), command.warehouseId());

        // TX1: Crear o recuperar Shipment en PENDING_ROUTING antes de hacer llamada externa
        Shipment initial = transactionTemplate.execute(status -> {
            Optional<Shipment> existing = shipmentRepository.findByOrderId(command.orderId());
            if (existing.isPresent()) {
                return existing.get();
            }
            Shipment pending = Shipment.createPendingRouting(
                    command.orderId(),
                    command.warehouseId(),
                    command.deliveryAddress()
            );
            return shipmentRepository.save(pending);
        });

        if (initial != null && initial.getState() != ShipmentState.PENDING_ROUTING) {
            log.info("Idempotencia: el despacho para la orden {} ya se encuentra en estado {}",
                    command.orderId(), initial.getState());
            return initial;
        }

        // Llamada externa fuera de la transacción (protegida por Resilience4j en el adaptador)
        RouteResult routeResult = routingPort.calculateRouteAndAssignDriver(
                command.orderId(),
                command.deliveryAddress()
        );

        // TX2: Aplicar transición (ROUTE_ASSIGNED o ROUTE_FAILED) y registrar Outbox
        return transactionTemplate.execute(status -> applyRoutingOutcome(command.orderId(), routeResult));
    }

    @Override
    public void retryPendingRouting(String orderId) {
        Optional<Shipment> existingOpt = shipmentRepository.findByOrderId(orderId);
        if (existingOpt.isEmpty() || existingOpt.get().getState() != ShipmentState.PENDING_ROUTING) {
            return;
        }
        Shipment pending = existingOpt.get();
        RouteResult routeResult = routingPort.calculateRouteAndAssignDriver(orderId, pending.getDeliveryAddress());
        transactionTemplate.execute(status -> applyRoutingOutcome(orderId, routeResult));
    }

    private Shipment applyRoutingOutcome(String orderId, RouteResult routeResult) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ShipmentNotFoundException(orderId));

        if (shipment.getState() != ShipmentState.PENDING_ROUTING) {
            log.info("Resultado de ruteo descartado: orden {} ya pasó a estado {}", orderId, shipment.getState());
            return shipment;
        }

        ShipmentEvent event = routeResult.successful() ? ShipmentEvent.ROUTE_ASSIGNED : ShipmentEvent.ROUTE_FAILED;
        Map<String, Object> params = routeResult.successful()
                ? Map.of(HEADER_ROUTE_ASSIGNMENT, routeResult.assignment())
                : Map.of(HEADER_REASON, routeResult.failureReason() != null ? routeResult.failureReason() : "Fallo en cálculo de ruta");

        TransitionResult<ShipmentState> result = stateMachine.fire(new ShipmentContext(shipment), event, params);
        if (!result.accepted()) {
            throw new InvalidStateTransitionException(orderId, result.source(), event);
        }

        shipment.changeState(result.target());
        Shipment saved = shipmentRepository.save(shipment);
        eventPublisher.publish(shipment.pullDomainEvents());

        log.info("Despacho para orden {} actualizado a estado {}", orderId, saved.getState());
        return saved;
    }

    @Override
    @Transactional
    public Shipment pickUp(PickUpShipmentCommand command) {
        Shipment shipment = shipmentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ShipmentNotFoundException(command.orderId()));

        String driverId = command.driverId() != null ? command.driverId() : shipment.getDriverId();
        TransitionResult<ShipmentState> result = stateMachine.fire(
                new ShipmentContext(shipment),
                ShipmentEvent.PICK_UP,
                Map.of(HEADER_DRIVER_ID, driverId != null ? driverId : "")
        );

        if (!result.accepted()) {
            throw new InvalidStateTransitionException(command.orderId(), result.source(), ShipmentEvent.PICK_UP);
        }

        shipment.changeState(result.target());
        Shipment saved = shipmentRepository.save(shipment);
        eventPublisher.publish(shipment.pullDomainEvents());
        return saved;
    }

    @Override
    @Transactional
    public Shipment deliver(DeliverShipmentCommand command) {
        Shipment shipment = shipmentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ShipmentNotFoundException(command.orderId()));

        TransitionResult<ShipmentState> result = stateMachine.fire(
                new ShipmentContext(shipment),
                ShipmentEvent.DELIVER
        );

        if (!result.accepted()) {
            throw new InvalidStateTransitionException(command.orderId(), result.source(), ShipmentEvent.DELIVER);
        }

        shipment.changeState(result.target());
        Shipment saved = shipmentRepository.save(shipment);
        eventPublisher.publish(shipment.pullDomainEvents());
        return saved;
    }

    @Override
    @Transactional
    public Shipment reportFailure(ReportDeliveryFailureCommand command) {
        Shipment shipment = shipmentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new ShipmentNotFoundException(command.orderId()));

        String reason = command.reason() != null ? command.reason() : "Entrega fallida en domicilio";
        TransitionResult<ShipmentState> result = stateMachine.fire(
                new ShipmentContext(shipment),
                ShipmentEvent.DELIVERY_FAILED,
                Map.of(HEADER_REASON, reason)
        );

        if (!result.accepted()) {
            if (shipment.getState().isTerminal()) {
                return shipment;
            }
            throw new InvalidStateTransitionException(command.orderId(), result.source(), ShipmentEvent.DELIVERY_FAILED);
        }

        shipment.changeState(result.target());
        Shipment saved = shipmentRepository.save(shipment);
        eventPublisher.publish(shipment.pullDomainEvents());
        return saved;
    }

    @Override
    @Transactional
    public Shipment cancel(CancelShipmentCommand command) {
        String reason = command.reason() != null ? command.reason() : "Cancelación solicitada";
        Optional<Shipment> existingOpt = shipmentRepository.findByOrderId(command.orderId());

        if (existingOpt.isEmpty()) {
            log.warn("No existe envío previo para orden {}. Creando tombstone CANCELLED.", command.orderId());
            Shipment tombstone = Shipment.createCancelledTombstone(command.orderId(), reason);
            Shipment saved = shipmentRepository.save(tombstone);
            eventPublisher.publish(tombstone.pullDomainEvents());
            return saved;
        }

        Shipment shipment = existingOpt.get();
        if (shipment.getState().isTerminal()) {
            log.info("CANCEL ignorado: el envío para la orden {} ya está en estado terminal {}",
                    command.orderId(), shipment.getState());
            return shipment;
        }

        TransitionResult<ShipmentState> result = stateMachine.fire(
                new ShipmentContext(shipment),
                ShipmentEvent.CANCEL,
                Map.of(HEADER_REASON, reason)
        );

        if (!result.accepted()) {
            throw new InvalidStateTransitionException(command.orderId(), result.source(), ShipmentEvent.CANCEL);
        }

        shipment.changeState(result.target());
        Shipment saved = shipmentRepository.save(shipment);
        eventPublisher.publish(shipment.pullDomainEvents());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> findByOrderId(String orderId) {
        return shipmentRepository.findByOrderId(orderId);
    }
}
