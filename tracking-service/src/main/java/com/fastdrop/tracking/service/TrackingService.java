package com.fastdrop.tracking.service;

import com.fastdrop.common.dto.ShipmentTrackingDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final RedisTemplate<String, ShipmentTrackingDTO> redisTemplate;

    @Value("${app.cache.tracking-ttl-seconds:30}")
    private long trackingTtlSeconds;

    private static final String CACHE_PREFIX = "shipment:tracking:";

    private static final Map<String, Integer> STAGE_PRECEDENCE = Map.of(
            "INITIALIZED", 0,
            "STOCK_RESERVED", 1,
            "DELIVERY_SCHEDULED", 2,
            "IN_TRANSIT", 3,
            "DELIVERY_FAILED", 4,
            "DELIVERED", 5,
            "STOCK_REJECTED", 5,
            "CANCELLED", 5
    );

    private final Map<String, ShipmentTrackingDTO> persistentStore = new ConcurrentHashMap<>();

    public Optional<ShipmentTrackingDTO> getTracking(String orderId) {
        String cacheKey = CACHE_PREFIX + orderId;
        log.info("Consultando tracking para Orden={}", orderId);

        try {
            ShipmentTrackingDTO cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("CACHE HIT [Redis] para orden {}", orderId);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Error de acceso a Redis, recurriendo a consulta primaria: {}", e.getMessage());
        }

        log.info("CACHE MISS para orden {}. Consultando fuente persistente...", orderId);
        ShipmentTrackingDTO dto = persistentStore.get(orderId);

        if (dto != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofSeconds(trackingTtlSeconds));
                log.info("Poblado Cache-Aside en Redis para orden {} con TTL={}s", orderId, trackingTtlSeconds);
            } catch (Exception e) {
                log.warn("Fallo al escribir en Redis: {}", e.getMessage());
            }
            return Optional.of(dto);
        }

        return Optional.empty();
    }

    public void invalidateCache(String orderId) {
        String cacheKey = CACHE_PREFIX + orderId;
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.info("Invalidación Reactiva de Cache: clave {} eliminada={}", cacheKey, deleted);
        } catch (Exception e) {
            log.warn("Fallo al invalidar clave en Redis {}: {}", cacheKey, e.getMessage());
        }
    }

    public synchronized void recordCheckpoint(String orderId, String stage, String description,
                                 String warehouseId, String driverId, String trackingNumber, String address) {
        ShipmentTrackingDTO dto = persistentStore.computeIfAbsent(orderId, id -> ShipmentTrackingDTO.builder()
                .orderId(id)
                .status("INITIALIZED")
                .checkpoints(new ArrayList<>())
                .lastUpdated(Instant.now())
                .build());

        int currentRank = STAGE_PRECEDENCE.getOrDefault(dto.getStatus(), 0);
        int incomingRank = STAGE_PRECEDENCE.getOrDefault(stage, 0);
        if (incomingRank >= currentRank) {
            dto.setStatus(stage);
        }
        dto.setLastUpdated(Instant.now());
        if (warehouseId != null) dto.setCurrentWarehouseId(warehouseId);
        if (driverId != null) dto.setDriverId(driverId);
        if (trackingNumber != null) dto.setTrackingNumber(trackingNumber);
        if (address != null) dto.setShippingAddress(address);

        dto.getCheckpoints().add(ShipmentTrackingDTO.TrackingCheckpoint.builder()
                .stage(stage)
                .description(description)
                .timestamp(Instant.now())
                .build());

        invalidateCache(orderId);
    }
}
