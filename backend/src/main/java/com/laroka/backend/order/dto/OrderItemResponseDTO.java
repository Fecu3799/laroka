package com.laroka.backend.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponseDTO {

    private UUID id;
    private Integer productId;
    private String productName;
    // US-HH-01: segunda mitad de un ítem mitad y mitad. Null en ítems simples.
    private Integer secondProductId;
    private String secondProductName;
    // Tamaño elegido (US-SIZE-03). Null cuando el ítem se pidió sin tamaño, que es el caso del
    // grande: es implícito y no tiene fila propia en product_size.
    private Integer productSizeId;
    private String sizeName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
