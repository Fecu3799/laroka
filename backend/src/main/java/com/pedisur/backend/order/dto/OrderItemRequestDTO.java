package com.pedisur.backend.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequestDTO {

    @NotNull
    private Integer productId;

    // US-HH-02: segunda mitad opcional (mitad y mitad). Null → ítem simple.
    private Integer secondProductId;

    // US-SIZE-03: tamaño opcional. Null → precio base del producto. Excluyente con
    // secondProductId: enviar ambos en el mismo ítem es 422.
    private Integer productSizeId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
