package com.fastdrop.tracking.service;

import com.fastdrop.common.dto.ShipmentTrackingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private RedisTemplate<String, ShipmentTrackingDTO> redisTemplate;

    @Mock
    private ValueOperations<String, ShipmentTrackingDTO> valueOperations;

    @InjectMocks
    private TrackingService trackingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(trackingService, "trackingTtlSeconds", 30L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Debe retornar dato desde Redis en caso de CACHE HIT")
    void shouldReturnCachedValueOnHit() {
        String orderId = "ORD-3001";
        ShipmentTrackingDTO cachedDto = ShipmentTrackingDTO.builder()
                .orderId(orderId)
                .status("DELIVERY_SCHEDULED")
                .driverId("DRV-007")
                .build();

        when(valueOperations.get("shipment:tracking:" + orderId)).thenReturn(cachedDto);

        Optional<ShipmentTrackingDTO> result = trackingService.getTracking(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("DELIVERY_SCHEDULED");
        assertThat(result.get().getDriverId()).isEqualTo("DRV-007");

        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("Debe consultar fuente primaria y poblar Redis con TTL en caso de CACHE MISS")
    void shouldFetchPrimaryStoreAndPopulateRedisOnMiss() {
        String orderId = "ORD-3002";
        when(valueOperations.get("shipment:tracking:" + orderId)).thenReturn(null);

        // Simulamos checkpoint previo en la fuente persistente
        trackingService.recordCheckpoint(orderId, "STOCK_RESERVED", "Stock reservado en depósito",
                "WH-CENTRAL-01", null, null, "Av. Santa Fe 1234");

        Optional<ShipmentTrackingDTO> result = trackingService.getTracking(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("STOCK_RESERVED");

        verify(valueOperations).set(eq("shipment:tracking:" + orderId), eq(result.get()), eq(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("Debe invalidar clave en Redis proactivamente al registrar nuevo checkpoint")
    void shouldInvalidateRedisCacheOnNewCheckpoint() {
        String orderId = "ORD-3003";

        trackingService.recordCheckpoint(orderId, "DELIVERED", "Paquete entregado",
                null, "DRV-101", "TRK-9999", null);

        verify(redisTemplate, atLeastOnce()).delete("shipment:tracking:" + orderId);
    }
}
