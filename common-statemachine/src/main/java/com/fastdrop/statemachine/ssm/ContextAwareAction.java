package com.fastdrop.statemachine.ssm;

import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

/**
 * Action base que extrae el sujeto tipado y captura cualquier {@link RuntimeException} en el
 * extended state para que {@link AbstractStateMachineAdapter} pueda relanzarla dentro de la misma
 * transacción.
 *
 * @param <S> enum de estados
 * @param <E> enum de eventos
 * @param <C> tipo del sujeto
 */
public abstract class ContextAwareAction<S, E, C> implements Action<S, E> {

    @Override
    public final void execute(StateContext<S, E> context) {
        try {
            C subject = StateContexts.subject(context);
            run(subject, context);
        } catch (RuntimeException ex) {
            context.getExtendedState().getVariables().put(AbstractStateMachineAdapter.ERROR_KEY, ex);
            throw ex;
        }
    }

    protected abstract void run(C subject, StateContext<S, E> context);
}
