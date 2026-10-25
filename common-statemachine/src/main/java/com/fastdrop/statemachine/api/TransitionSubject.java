package com.fastdrop.statemachine.api;

/**
 * Representa el sujeto sobre el cual se ejecuta una transición de máquina de estados.
 * Libre de dependencias de framework para poder utilizarse desde la capa de aplicación.
 *
 * @param <S> enum de estados
 */
public interface TransitionSubject<S extends Enum<S>> {

    /**
     * Identificador de correlación de la instancia de máquina (por ejemplo, orderId).
     */
    String machineId();

    /**
     * Estado actual persistido desde el cual se rehidrata la máquina.
     */
    S currentState();
}
