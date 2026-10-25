package com.fastdrop.statemachine.ssm;

import org.springframework.statemachine.StateContext;

/**
 * Utilitario para extraer de forma tipada el sujeto y los headers desde un {@link StateContext}.
 */
public final class StateContexts {

    private StateContexts() {
    }

    @SuppressWarnings("unchecked")
    public static <C, S, E> C subject(StateContext<S, E> context) {
        Object value = context.getMessageHeader(AbstractStateMachineAdapter.SUBJECT_HEADER);
        if (value == null) {
            throw new IllegalStateException("No se encontró el header '" + AbstractStateMachineAdapter.SUBJECT_HEADER + "' en el StateContext");
        }
        return (C) value;
    }

    @SuppressWarnings("unchecked")
    public static <T, S, E> T header(StateContext<S, E> context, String name) {
        return (T) context.getMessageHeader(name);
    }

    public static <S, E> String headerOrDefault(StateContext<S, E> context, String name, String defaultValue) {
        Object val = context.getMessageHeader(name);
        return val != null ? val.toString() : defaultValue;
    }
}
