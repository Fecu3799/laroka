package com.pedisur.backend.shift.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.pedisur.backend.order.entity.DiscountReason;
import com.pedisur.backend.order.entity.OrderOrigin;
import com.pedisur.backend.order.entity.OrderStatus;
import com.pedisur.backend.order.entity.PaymentMethod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una fila del detalle de pedidos del turno para el PDF de cierre (US-20-03).
 *
 * Se resuelve on-demand contra Order al generar el PDF, no se persiste: los pedidos
 * de un turno cerrado son terminales (DELIVERED/CANCELLED, RN-13) e inmutables, así
 * que reconstruir el detalle da siempre el mismo resultado. Ver getShiftOrderDetails.
 *
 * discountAmount/discountReason vienen null si el pedido no tiene un descuento vigente
 * APPLIED (un descuento revertido no cuenta, US-19-06).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftOrderDetailDTO {

    private LocalDateTime createdAt;
    private Long orderNumber;
    private OrderOrigin origin;
    private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private BigDecimal discountAmount;
    private DiscountReason discountReason;
}
