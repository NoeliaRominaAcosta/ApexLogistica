package com.fastdrop.orchestrator.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "El orderId es obligatorio")
    private String orderId;

    private String customerId;

    @NotBlank(message = "El SKU es obligatorio")
    private String sku;

    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private int quantity;

    private String warehouseId;

    @NotBlank(message = "La dirección de entrega es obligatoria")
    private String deliveryAddress;
}
