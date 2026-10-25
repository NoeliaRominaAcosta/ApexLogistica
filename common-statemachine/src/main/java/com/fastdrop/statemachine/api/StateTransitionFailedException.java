package com.fastdrop.statemachine.api;

/**
 * Excepción lanzada cuando una acción de la máquina de estados arroja un error durante la transición.
 */
public class StateTransitionFailedException extends RuntimeException {

    private final String machineId;
    private final Enum<?> sourceState;
    private final Enum<?> event;

    public StateTransitionFailedException(String machineId, Enum<?> sourceState, Enum<?> event, Throwable cause) {
        super(String.format("Fallo ejecutando transición para [id=%s] desde estado %s con evento %s: %s",
                machineId, sourceState, event, cause != null ? cause.getMessage() : "sin detalle"), cause);
        this.machineId = machineId;
        this.sourceState = sourceState;
        this.event = event;
    }

    public String getMachineId() {
        return machineId;
    }

    public Enum<?> getSourceState() {
        return sourceState;
    }

    public Enum<?> getEvent() {
        return event;
    }
}
