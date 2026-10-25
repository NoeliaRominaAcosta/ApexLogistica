package com.fastdrop.dispatch.controller;

import com.fastdrop.dispatch.client.ExternalRoutingServiceClient;
import com.fastdrop.dispatch.domain.Shipment;
import com.fastdrop.dispatch.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class DispatchController {

    private final ShipmentRepository shipmentRepository;
    private final ExternalRoutingServiceClient routingServiceClient;

    @GetMapping("/{orderId}")
    public ResponseEntity<Shipment> getShipment(@PathVariable String orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/simulate-fail")
    public ResponseEntity<Map<String, Object>> toggleSimulateFailure(@RequestParam(defaultValue = "true") boolean enabled) {
        routingServiceClient.setForceFailure(enabled);
        return ResponseEntity.ok(Map.of(
                "forcedFailureEnabled", enabled,
                "message", enabled
                        ? "Fallo forzado en API de enrutamiento activado. Las próximas reservas dispararán compensación Saga."
                        : "Fallo forzado desactivado. Las operaciones continuarán normalmente."
        ));
    }
}
