package com.fastdrop.dispatch.infrastructure.adapter.in.rest;

import com.fastdrop.dispatch.application.port.in.*;
import com.fastdrop.dispatch.application.port.in.command.CancelShipmentCommand;
import com.fastdrop.dispatch.application.port.in.command.DeliverShipmentCommand;
import com.fastdrop.dispatch.application.port.in.command.PickUpShipmentCommand;
import com.fastdrop.dispatch.application.port.in.command.ReportDeliveryFailureCommand;
import com.fastdrop.dispatch.application.port.out.RoutingPort;
import com.fastdrop.dispatch.domain.model.Shipment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final QueryShipmentUseCase queryShipmentUseCase;
    private final PickUpShipmentUseCase pickUpShipmentUseCase;
    private final DeliverShipmentUseCase deliverShipmentUseCase;
    private final ReportDeliveryFailureUseCase reportDeliveryFailureUseCase;
    private final CancelShipmentUseCase cancelShipmentUseCase;
    private final RoutingPort routingPort;

    @GetMapping("/{orderId}")
    public ResponseEntity<Shipment> getShipment(@PathVariable String orderId) {
        return queryShipmentUseCase.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/pick-up")
    public ResponseEntity<Shipment> pickUpShipment(
            @PathVariable String orderId,
            @RequestParam(required = false) String driverId) {
        return ResponseEntity.ok(pickUpShipmentUseCase.pickUp(new PickUpShipmentCommand(orderId, driverId)));
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<Shipment> deliverShipment(@PathVariable String orderId) {
        return ResponseEntity.ok(deliverShipmentUseCase.deliver(new DeliverShipmentCommand(orderId)));
    }

    @PostMapping("/{orderId}/delivery-failed")
    public ResponseEntity<Shipment> reportDeliveryFailure(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Destinatario ausente / domicilio inaccesible") String reason) {
        return ResponseEntity.ok(reportDeliveryFailureUseCase.reportFailure(
                new ReportDeliveryFailureCommand(orderId, reason)
        ));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Shipment> cancelShipment(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Cancelación manual de envío") String reason) {
        return ResponseEntity.ok(cancelShipmentUseCase.cancel(new CancelShipmentCommand(orderId, reason)));
    }

    @PostMapping("/simulate-fail")
    public ResponseEntity<Map<String, Object>> toggleSimulateFailure(
            @RequestParam(defaultValue = "true") boolean enabled) {
        routingPort.setForceFailure(enabled);
        return ResponseEntity.ok(Map.of(
                "forcedFailureEnabled", enabled,
                "message", enabled
                        ? "Fallo forzado en API de enrutamiento activado. Las próximas reservas dispararán compensación Saga."
                        : "Fallo forzado desactivado. Las operaciones continuarán normalmente."
        ));
    }
}
