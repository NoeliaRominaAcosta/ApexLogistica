package com.fastdrop.warehouse.dto;

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
public class ReserveStockRequest {
    @NotBlank(message = "orderId es requerido")
    private String orderId;

    @NotBlank(message = "sku es requerido")
    private String sku;

    private String warehouseId;

    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private int quantity;

    @NotBlank(message = "La dirección de entrega es requerida")
    private String deliveryAddress;

    private String customerId;
}
