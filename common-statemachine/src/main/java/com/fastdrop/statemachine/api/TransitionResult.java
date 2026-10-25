package com.fastdrop.statemachine.api;

/**
 * Resultado de disparar un evento sobre una máquina de estados.
 *
 * @param accepted {@code true} si el evento fue aceptado por la máquina
 * @param source   estado de origen antes de disparar el evento
 * @param target   estado resultante tras disparar el evento
 * @param <S>      enum de estados
 */
public record TransitionResult<S extends Enum<S>>(boolean accepted, S source, S target) {

    public boolean changed() {
        return accepted && source != target;
    }
}
