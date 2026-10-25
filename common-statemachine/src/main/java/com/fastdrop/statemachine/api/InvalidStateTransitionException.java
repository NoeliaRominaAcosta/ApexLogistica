package com.fastdrop.statemachine.api;

/**
 * Excepción lanzada cuando una transición es rechazada (DENIED) desde un estado no terminal.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final String machineId;
    private final Enum<?> currentState;
    private final Enum<?> event;

    public InvalidStateTransitionException(String machineId, Enum<?> currentState, Enum<?> event) {
        super(String.format("Transición inválida para [id=%s]: evento %s no permitido desde el estado %s",
                machineId, event, currentState));
        this.machineId = machineId;
        this.currentState = currentState;
        this.event = event;
    }

    public String getMachineId() {
        return machineId;
    }

    public Enum<?> getCurrentState() {
        return currentState;
    }

    public Enum<?> getEvent() {
        return event;
    }
}
