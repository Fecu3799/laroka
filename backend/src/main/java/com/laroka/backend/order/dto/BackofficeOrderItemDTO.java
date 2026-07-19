package com.laroka.backend.order.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackofficeOrderItemDTO {

    private String productName;
    // US-HH-01: segunda mitad de un ítem mitad y mitad. Null en ítems simples.
    private String secondProductName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
