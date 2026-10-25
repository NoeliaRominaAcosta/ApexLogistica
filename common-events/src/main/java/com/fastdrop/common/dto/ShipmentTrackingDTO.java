package com.fastdrop.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentTrackingDTO implements Serializable {
    private String orderId;
    private String shipmentId;
    private String trackingNumber;
    private String status; // INITIALIZED, STOCK_RESERVED, STOCK_REJECTED, DELIVERY_SCHEDULED, IN_TRANSIT, DELIVERED, DELIVERY_FAILED, CANCELLED
    private String currentWarehouseId;
    private String driverId;
    private String shippingAddress;
    private String estimatedDelivery;
    private Instant lastUpdated;
    private List<TrackingCheckpoint> checkpoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingCheckpoint implements Serializable {
        private String stage;
        private String description;
        private Instant timestamp;
    }
}
