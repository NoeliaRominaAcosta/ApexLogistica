package com.fastdrop.statemachine.api;

import java.util.Collections;
import java.util.Map;

/**
 * Puerto de salida genérico para evaluar y ejecutar transiciones de estado.
 *
 * @param <C> tipo del sujeto/contexto de transición
 * @param <S> enum de estados
 * @param <E> enum de eventos (triggers)
 */
public interface StateMachinePort<C extends TransitionSubject<S>, S extends Enum<S>, E extends Enum<E>> {

    TransitionResult<S> fire(C subject, E event, Map<String, Object> params);

    default TransitionResult<S> fire(C subject, E event) {
        return fire(subject, event, Collections.emptyMap());
    }
}
