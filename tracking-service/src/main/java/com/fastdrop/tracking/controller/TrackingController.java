package com.fastdrop.tracking.controller;

import com.fastdrop.common.dto.ShipmentTrackingDTO;
import com.fastdrop.tracking.service.TrackingService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @GetMapping("/{orderId}/tracking")
    @RateLimiter(name = "trackingApi", fallbackMethod = "trackingRateLimitFallback")
    public ResponseEntity<?> getTracking(@PathVariable String orderId) {
        log.info("Endpoint de tracking invocado en Thread: {}", Thread.currentThread());
        return trackingService.getTracking(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ShipmentTrackingDTO.builder()
                                .orderId(orderId)
                                .status("NOT_FOUND")
                                .build()));
    }

    @DeleteMapping("/{orderId}/cache")
    public ResponseEntity<Map<String, String>> evictCache(@PathVariable String orderId) {
        trackingService.invalidateCache(orderId);
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "message", "Cache de tracking invalidada manualmente con éxito"
        ));
    }

    public ResponseEntity<?> trackingRateLimitFallback(String orderId, RequestNotPermitted ex) {
        log.warn("Rate limit excedido para consulta de tracking en orden: {}", orderId);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "error", "Too Many Requests",
                        "message", "Límite de consultas de tracking excedido. Por favor espere unos segundos."
                ));
    }
}
