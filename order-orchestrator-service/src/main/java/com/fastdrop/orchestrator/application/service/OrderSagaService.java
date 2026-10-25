package com.fastdrop.orchestrator.application.service;

import com.fastdrop.orchestrator.application.port.in.CancelOrderSagaUseCase;
import com.fastdrop.orchestrator.application.port.in.HandleParticipantReplyUseCase;
import com.fastdrop.orchestrator.application.port.in.HandleSagaTimeoutUseCase;
import com.fastdrop.orchestrator.application.port.in.StartOrderSagaUseCase;
import com.fastdrop.orchestrator.application.port.in.command.CancelOrderSagaCommand;
import com.fastdrop.orchestrator.application.port.in.command.ParticipantReplyCommand;
import com.fastdrop.orchestrator.application.port.in.command.StartOrderSagaCommand;
import com.fastdrop.orchestrator.application.port.out.OrderSagaRepositoryPort;
import com.fastdrop.orchestrator.application.port.out.ProcessedMessagePort;
import com.fastdrop.orchestrator.application.port.out.SagaCommandPublisherPort;
import com.fastdrop.orchestrator.application.port.out.SagaContext;
import com.fastdrop.orchestrator.application.port.out.SagaStateMachinePort;
import com.fastdrop.orchestrator.domain.exception.OrderSagaNotFoundException;
import com.fastdrop.orchestrator.domain.model.OrderSaga;
import com.fastdrop.orchestrator.domain.model.SagaEvent;
import com.fastdrop.orchestrator.domain.model.SagaState;
import com.fastdrop.statemachine.api.InvalidStateTransitionException;
import com.fastdrop.statemachine.api.TransitionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaService implements
        StartOrderSagaUseCase,
        CancelOrderSagaUseCase,
        HandleParticipantReplyUseCase,
        HandleSagaTimeoutUseCase {

    public static final String HEADER_WAREHOUSE_ID = "warehouseId";
    public static final String HEADER_SHIPMENT_ID = "shipmentId";
    public static final String HEADER_TRACKING_NUMBER = "trackingNumber";
    public static final String HEADER_DRIVER_ID = "driverId";
    public static final String HEADER_REASON = "reason";

    private final OrderSagaRepositoryPort sagaRepository;
    private final ProcessedMessagePort processedMessagePort;
    private final SagaStateMachinePort stateMachine;
    private final SagaCommandPublisherPort commandPublisher;

    @Value("${app.saga.step-timeout-seconds:120}")
    private long stepTimeoutSeconds;

    @Override
    @Transactional
    public OrderSaga start(StartOrderSagaCommand command) {
        log.info("Iniciando OrderSaga para Orden={}, SKU={}, Cantidad={}",
                command.orderId(), command.sku(), command.quantity());

        Optional<OrderSaga> existing = sagaRepository.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            log.warn("Idempotencia: la saga para la orden {} ya existe en estado {}",
                    command.orderId(), existing.get().getState());
            return existing.get();
        }

        OrderSaga saga = OrderSaga.createNew(
                command.orderId(),
                command.customerId(),
                command.sku(),
                command.quantity(),
                command.warehouseId(),
                command.deliveryAddress()
        );

        TransitionResult<SagaState> result = stateMachine.fire(new SagaContext(saga), SagaEvent.START);
        if (!result.accepted()) {
            throw new InvalidStateTransitionException(command.orderId(), result.source(), SagaEvent.START);
        }

        saga.changeState(result.target(), Duration.ofSeconds(stepTimeoutSeconds));
        OrderSaga saved = sagaRepository.save(saga);
        commandPublisher.publish(saga.pullOutboundCommands());
        return saved;
    }

    @Override
    @Transactional
    public OrderSaga onParticipantReply(ParticipantReplyCommand command) {
        if (command.eventId() != null && !command.eventId().isBlank()
                && processedMessagePort.isAlreadyProcessed(command.eventId())) {
            log.info("Mensaje duplicado ignorado en Orquestador: eventId={}, orderId={}",
                    command.eventId(), command.orderId());
            return sagaRepository.findByOrderId(command.orderId()).orElse(null);
        }

        Optional<OrderSaga> sagaOpt = sagaRepository.findByOrderId(command.orderId());
        if (sagaOpt.isEmpty()) {
            log.debug("Respuesta {} ignorada: la orden {} no está gestionada por este orquestador",
                    command.sagaEvent(), command.orderId());
            return null;
        }

        OrderSaga saga = sagaOpt.get();
        TransitionResult<SagaState> result = stateMachine.fire(
                new SagaContext(saga),
                command.sagaEvent(),
                command.metadata() != null ? command.metadata() : Map.of()
        );

        if (command.eventId() != null && !command.eventId().isBlank()) {
            processedMessagePort.markProcessed(command.eventId(), command.orderId(), command.sagaEvent().name());
        }

        if (!result.accepted()) {
            if (saga.getState().isTerminal()) {
                log.info("Evento {} ignorado: la saga {} ya se encuentra en estado terminal {}",
                        command.sagaEvent(), command.orderId(), saga.getState());
                return saga;
            }
            log.warn("Evento {} no aplicable para saga {} en estado actual {}",
                    command.sagaEvent(), command.orderId(), saga.getState());
            return saga;
        }

        saga.changeState(result.target(), Duration.ofSeconds(stepTimeoutSeconds));
        OrderSaga saved = sagaRepository.save(saga);
        commandPublisher.publish(saga.pullOutboundCommands());

        log.info("OrderSaga {} avanzó: {} --({})--> {}",
                command.orderId(), result.source(), command.sagaEvent(), saved.getState());
        return saved;
    }

    @Override
    @Transactional
    public OrderSaga cancel(CancelOrderSagaCommand command) {
        OrderSaga saga = sagaRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException(command.orderId()));

        if (saga.getState().isTerminal()) {
            return saga;
        }

        String reason = command.reason() != null ? command.reason() : "Cancelación solicitada por usuario";
        TransitionResult<SagaState> result = stateMachine.fire(
                new SagaContext(saga),
                SagaEvent.CANCEL_REQUESTED,
                Map.of(HEADER_REASON, reason)
        );

        if (!result.accepted()) {
            throw new InvalidStateTransitionException(command.orderId(), result.source(), SagaEvent.CANCEL_REQUESTED);
        }

        saga.changeState(result.target(), Duration.ofSeconds(stepTimeoutSeconds));
        OrderSaga saved = sagaRepository.save(saga);
        commandPublisher.publish(saga.pullOutboundCommands());
        return saved;
    }

    @Override
    @Transactional
    public void handleExpiredSagas() {
        List<OrderSaga> expired = sagaRepository.findExpiredSagas(Instant.now(), 50);
        for (OrderSaga saga : expired) {
            if (saga.getState().isTerminal()) {
                continue;
            }
            log.warn("Timeout detectado en OrderSaga orderId={} en estado {}", saga.getOrderId(), saga.getState());
            TransitionResult<SagaState> result = stateMachine.fire(
                    new SagaContext(saga),
                    SagaEvent.TIMEOUT,
                    Map.of(HEADER_REASON, "Timeout expirado en estado " + saga.getState())
            );
            if (result.accepted()) {
                saga.changeState(result.target(), Duration.ofSeconds(stepTimeoutSeconds));
                sagaRepository.save(saga);
                commandPublisher.publish(saga.pullOutboundCommands());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSaga> findByOrderId(String orderId) {
        return sagaRepository.findByOrderId(orderId);
    }
}
