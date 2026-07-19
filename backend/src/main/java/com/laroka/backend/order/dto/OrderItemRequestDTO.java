package com.laroka.backend.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequestDTO {

    @NotNull
    private Integer productId;

    // US-HH-02: segunda mitad opcional (mitad y mitad). Null → ítem simple.
    private Integer secondProductId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
