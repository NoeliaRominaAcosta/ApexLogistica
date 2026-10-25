package com.fastdrop.dispatch.domain.model;

/**
 * Value Object con los datos de una ruta asignada.
 */
public record RouteAssignment(
        String driverId,
        String trackingNumber,
        String estimatedDelivery,
        String shippingLabelUrl
) {
}
