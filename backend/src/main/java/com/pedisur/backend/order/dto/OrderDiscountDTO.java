package com.pedisur.backend.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.pedisur.backend.order.entity.DiscountReason;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Descuento vigente de un pedido (US-19-03): la fila más reciente de
 * {@code order_discount}. Expone el cálculo y su trazabilidad —quién, cuándo y por
 * qué— para que un ajuste de precio nunca aparezca sin explicación en el detalle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDiscountDTO {

    private BigDecimal percentage;
    private BigDecimal originalTotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalTotalAmount;
    private DiscountReason reason;
    private String note;
    /** Nombre del MANAGER/ADMIN que lo aplicó. Null si el usuario ya no existe. */
    private String appliedByName;
    private LocalDateTime appliedAt;
}
