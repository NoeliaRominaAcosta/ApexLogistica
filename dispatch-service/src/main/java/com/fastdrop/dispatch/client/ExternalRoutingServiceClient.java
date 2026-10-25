package com.fastdrop.dispatch.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class ExternalRoutingServiceClient {

    private final AtomicBoolean forceFailure = new AtomicBoolean(false);
    private final Random random = new Random();

    public void setForceFailure(boolean fail) {
        this.forceFailure.set(fail);
        log.warn("Modo de fallo forzado de enrutamiento configurado a: {}", fail);
    }

    public boolean isForceFailure() {
        return this.forceFailure.get();
    }

    @CircuitBreaker(name = "routingService", fallbackMethod = "routingFallback")
    @Retry(name = "routingService")
    public RouteAssignmentResult calculateRouteAndAssignDriver(String orderId, String deliveryAddress) {
        log.info("Llamando a la API Externa de Enrutamiento para Orden={}, Dirección={}", orderId, deliveryAddress);

        // Si se fuerza fallo o la dirección contiene 'FAIL', simulamos error en API externa
        if (forceFailure.get() || (deliveryAddress != null && deliveryAddress.toUpperCase().contains("FAIL"))) {
            log.warn("Simulando falla de red / indisponibilidad de flota en API externa de enrutamiento");
            throw new RuntimeException("API de Enrutamiento Externa no disponible / Sin flota en la zona");
        }

        // Asignación exitosa normal
        String driverId = "DRV-" + (100 + random.nextInt(900));
        String trackingNum = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String estDelivery = "Hoy antes de las 20:00";
        String labelUrl = "https://labels.fastdrop.internal/shipments/" + orderId + ".pdf";

        log.info("Ruta calculada con éxito. Driver={}, TrackingNum={}", driverId, trackingNum);

        return RouteAssignmentResult.builder()
                .successful(true)
                .driverId(driverId)
                .trackingNumber(trackingNum)
                .estimatedDelivery(estDelivery)
                .shippingLabelUrl(labelUrl)
                .fallbackTriggered(false)
                .build();
    }

    public RouteAssignmentResult routingFallback(String orderId, String deliveryAddress, Throwable t) {
        log.error("CircuitBreaker / Retry FALLBACK activado para Orden={}. Causa: {}", orderId, t.getMessage());
        return RouteAssignmentResult.builder()
                .successful(false)
                .failureReason("No hay conductores ni rutas viables disponibles (CircuitBreaker/Fallback activo): " + t.getMessage())
                .fallbackTriggered(true)
                .build();
    }
}
