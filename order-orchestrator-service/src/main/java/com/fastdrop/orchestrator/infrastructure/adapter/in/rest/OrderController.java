package com.fastdrop.orchestrator.infrastructure.adapter.in.rest;

import com.fastdrop.orchestrator.application.port.in.CancelOrderSagaUseCase;
import com.fastdrop.orchestrator.application.port.in.HandleSagaTimeoutUseCase;
import com.fastdrop.orchestrator.application.port.in.StartOrderSagaUseCase;
import com.fastdrop.orchestrator.application.port.in.command.CancelOrderSagaCommand;
import com.fastdrop.orchestrator.application.port.in.command.StartOrderSagaCommand;
import com.fastdrop.orchestrator.domain.model.OrderSaga;
import com.fastdrop.orchestrator.infrastructure.adapter.in.rest.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final StartOrderSagaUseCase startOrderSagaUseCase;
    private final CancelOrderSagaUseCase cancelOrderSagaUseCase;
    private final HandleSagaTimeoutUseCase queryOrderSagaUseCase;

    @PostMapping
    public ResponseEntity<OrderSaga> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderSaga saga = startOrderSagaUseCase.start(new StartOrderSagaCommand(
                request.getOrderId(),
                request.getCustomerId(),
                request.getSku(),
                request.getQuantity(),
                request.getWarehouseId(),
                request.getDeliveryAddress()
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(saga);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderSaga> getOrderSaga(@PathVariable String orderId) {
        return queryOrderSagaUseCase.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderSaga> cancelOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Cancelación solicitada por el cliente") String reason) {
        OrderSaga saga = cancelOrderSagaUseCase.cancel(new CancelOrderSagaCommand(orderId, reason));
        return ResponseEntity.ok(saga);
    }
}
