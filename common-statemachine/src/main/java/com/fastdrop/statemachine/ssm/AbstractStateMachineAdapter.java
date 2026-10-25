package com.fastdrop.statemachine.ssm;

import com.fastdrop.statemachine.api.StateMachinePort;
import com.fastdrop.statemachine.api.StateTransitionFailedException;
import com.fastdrop.statemachine.api.TransitionResult;
import com.fastdrop.statemachine.api.TransitionSubject;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Adaptador base de Spring State Machine que:
 * <ol>
 *   <li>Obtiene una instancia nueva de {@link StateMachineFactory} para el {@code machineId}.</li>
 *   <li>La rehidrata desde el estado persistido del agregado.</li>
 *   <li>Dispara el evento de forma síncrona en el mismo hilo (preservando la transacción actual).</li>
 *   <li>Propaga cualquier excepción ocurrida dentro de una action como {@link StateTransitionFailedException}.</li>
 * </ol>
 *
 * @param <C> tipo del sujeto/contexto
 * @param <S> enum de estados
 * @param <E> enum de eventos
 */
public abstract class AbstractStateMachineAdapter<C extends TransitionSubject<S>, S extends Enum<S>, E extends Enum<E>>
        implements StateMachinePort<C, S, E> {

    public static final String SUBJECT_HEADER = "fastdrop.sm.subject";
    public static final String ERROR_KEY = "fastdrop.sm.error";

    private final StateMachineFactory<S, E> factory;

    protected AbstractStateMachineAdapter(StateMachineFactory<S, E> factory) {
        this.factory = factory;
    }

    @Override
    public final TransitionResult<S> fire(C subject, E event, Map<String, Object> params) {
        S source = subject.currentState();
        StateMachine<S, E> sm = factory.getStateMachine(subject.machineId());
        try {
            // 1) Rehidratar desde el estado actual del agregado
            sm.stopReactively().block();
            sm.getStateMachineAccessor().doWithAllRegions(acc ->
                    acc.resetStateMachineReactively(
                            new DefaultStateMachineContext<>(source, null, null, null, null, sm.getId())
                    ).block()
            );
            sm.startReactively().block();

            // 2) Construir mensaje con el sujeto y parámetros adicionales como headers
            MessageBuilder<E> builder = MessageBuilder.withPayload(event)
                    .setHeader(SUBJECT_HEADER, subject);
            if (params != null && !params.isEmpty()) {
                builder.copyHeaders(params);
            }
            Message<E> message = builder.build();

            // 3) Enviar el evento de forma síncrona (mismo hilo -> misma transacción)
            StateMachineEventResult<S, E> eventResult = sm.sendEvent(Mono.just(message)).blockLast();

            // 4) Verificar si alguna action registró un error en el extended state
            RuntimeException actionError = sm.getExtendedState().get(ERROR_KEY, RuntimeException.class);
            if (actionError != null) {
                throw new StateTransitionFailedException(subject.machineId(), source, event, actionError);
            }
            if (sm.hasStateMachineError()) {
                throw new StateTransitionFailedException(subject.machineId(), source, event, null);
            }

            boolean accepted = eventResult != null
                    && eventResult.getResultType() == StateMachineEventResult.ResultType.ACCEPTED;
            S target = sm.getState() != null ? sm.getState().getId() : source;
            return new TransitionResult<>(accepted, source, target);
        } finally {
            sm.stopReactively().block();
        }
    }
}
