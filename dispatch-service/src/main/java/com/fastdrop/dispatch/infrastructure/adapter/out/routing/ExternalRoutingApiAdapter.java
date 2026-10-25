package com.fastdrop.dispatch.infrastructure.adapter.out.routing;

import com.fastdrop.dispatch.application.port.out.RouteResult;
import com.fastdrop.dispatch.application.port.out.RoutingPort;
import com.fastdrop.dispatch.domain.model.RouteAssignment;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ExternalRoutingApiAdapter implements RoutingPort {

    private final AtomicBoolean forceFailure = new AtomicBoolean(false);
    private final Random random = new Random();

    @Override
    public void setForceFailure(boolean fail) {
        this.forceFailure.set(fail);
        log.warn("Modo de fallo forzado de enrutamiento configurado a: {}", fail);
    }

    @Override
    public boolean isForceFailure() {
        return this.forceFailure.get();
    }

    @Override
    @CircuitBreaker(name = "routingService", fallbackMethod = "routingFallback")
    @Retry(name = "routingService")
    public RouteResult calculateRouteAndAssignDriver(String orderId, String deliveryAddress) {
        log.info("Llamando a la API Externa de Enrutamiento para Orden={}, Dirección={}", orderId, deliveryAddress);

        if (forceFailure.get() || (deliveryAddress != null && deliveryAddress.toUpperCase().contains("FAIL"))) {
            log.warn("Simulando falla de red / indisponibilidad de flota en API externa de enrutamiento");
            throw new RuntimeException("API de Enrutamiento Externa no disponible / Sin flota en la zona");
        }

        String driverId = "DRV-" + (100 + random.nextInt(900));
        String trackingNum = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String estDelivery = "Hoy antes de las 20:00";
        String labelUrl = "https://labels.fastdrop.internal/shipments/" + orderId + ".pdf";

        log.info("Ruta calculada con éxito. Driver={}, TrackingNum={}", driverId, trackingNum);

        return new RouteResult(
                true,
                new RouteAssignment(driverId, trackingNum, estDelivery, labelUrl),
                null,
                false
        );
    }

    public RouteResult routingFallback(String orderId, String deliveryAddress, Throwable t) {
        log.error("CircuitBreaker / Retry FALLBACK activado para Orden={}. Causa: {}", orderId, t.getMessage());
        return new RouteResult(
                false,
                null,
                "No hay conductores ni rutas viables disponibles (CircuitBreaker/Fallback activo): " + t.getMessage(),
                true
        );
    }
}
