package com.fastdrop.dispatch.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteAssignmentResult {
    private boolean successful;
    private String driverId;
    private String trackingNumber;
    private String estimatedDelivery;
    private String shippingLabelUrl;
    private String failureReason;
    private boolean fallbackTriggered;
}
