package com.fastdrop.orchestrator.infrastructure.adapter.in.rest;

import com.fastdrop.orchestrator.domain.exception.OrderSagaNotFoundException;
import com.fastdrop.statemachine.api.InvalidStateTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidStateTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "INVALID_STATE_TRANSITION",
                "message", ex.getMessage(),
                "machineId", ex.getMachineId(),
                "currentState", ex.getCurrentState().name(),
                "event", ex.getEvent().name(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(OrderSagaNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(OrderSagaNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
