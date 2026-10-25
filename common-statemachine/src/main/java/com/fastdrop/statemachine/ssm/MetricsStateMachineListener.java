package com.fastdrop.statemachine.ssm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

/**
 * Listener de auditoría y telemetría para máquinas de estado.
 * Registra cada cambio de estado y los eventos rechazados (DENIED), e incrementa contadores Micrometer
 * si se provee un {@link MeterRegistry}.
 *
 * @param <S> enum de estados
 * @param <E> enum de eventos
 */
@Slf4j
public class MetricsStateMachineListener<S extends Enum<S>, E extends Enum<E>>
        extends StateMachineListenerAdapter<S, E> {

    private final String machineName;
    private final MeterRegistry meterRegistry;

    public MetricsStateMachineListener(String machineName, MeterRegistry meterRegistry) {
        this.machineName = machineName;
        this.meterRegistry = meterRegistry;
    }

    public MetricsStateMachineListener(String machineName) {
        this(machineName, null);
    }

    @Override
    public void transition(Transition<S, E> transition) {
        if (transition == null || transition.getTarget() == null) {
            return;
        }
        String source = transition.getSource() != null && transition.getSource().getId() != null
                ? transition.getSource().getId().name()
                : "INITIAL";
        String target = transition.getTarget().getId() != null
                ? transition.getTarget().getId().name()
                : "UNKNOWN";
        String trigger = transition.getTrigger() != null && transition.getTrigger().getEvent() != null
                ? transition.getTrigger().getEvent().name()
                : "AUTO";

        log.debug("[StateMachine:{}] Transición {} --({})--> {}", machineName, source, trigger, target);

        if (meterRegistry != null && !"INITIAL".equals(source)) {
            Counter.builder("fastdrop.statemachine.transitions")
                    .tag("machine", machineName)
                    .tag("source", source)
                    .tag("target", target)
                    .tag("event", trigger)
                    .register(meterRegistry)
                    .increment();
        }
    }

    @Override
    public void eventNotAccepted(Message<E> event) {
        String eventName = event != null && event.getPayload() != null ? event.getPayload().name() : "UNKNOWN";
        log.warn("[StateMachine:{}] Evento no aceptado (DENIED): {}", machineName, eventName);
        if (meterRegistry != null) {
            Counter.builder("fastdrop.statemachine.denied")
                    .tag("machine", machineName)
                    .tag("event", eventName)
                    .register(meterRegistry)
                    .increment();
        }
    }

    @Override
    public void stateChanged(State<S, E> from, State<S, E> to) {
        // Cubierto por transition(...)
    }
}
