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
public class OrderItemStatusDTO {
    private String name;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
