package com.fastdrop.statemachine.ssm;

import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;

/**
 * Guard base que extrae el sujeto tipado del contexto del mensaje antes de evaluar la condición.
 *
 * @param <S> enum de estados
 * @param <E> enum de eventos
 * @param <C> tipo del sujeto
 */
public abstract class ContextAwareGuard<S, E, C> implements Guard<S, E> {

    @Override
    public final boolean evaluate(StateContext<S, E> context) {
        C subject = StateContexts.subject(context);
        return test(subject, context);
    }

    protected abstract boolean test(C subject, StateContext<S, E> context);
}
