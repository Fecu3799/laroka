package com.pedisur.backend.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.pedisur.backend.order.entity.OrderOrigin;
import com.pedisur.backend.order.entity.OrderStatus;
import com.pedisur.backend.order.entity.OrderType;
import com.pedisur.backend.order.entity.PaymentMethod;
import com.pedisur.backend.payment.entity.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackofficeOrderResponseDTO {

    private UUID id;
    private Long orderNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private OrderStatus status;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    // US-19-04: descuento vigente, para que el ticket impreso desde la lista pueda
    // explicar el TOTAL. null = sin descuento. appliedByName no viene resuelto acá
    // (ver OrderMapper); el detalle de US-19-03 sí lo expone.
    private OrderDiscountDTO discount;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private OrderType orderType;
    private OrderOrigin origin;
    private String deliveryAddress;
    private String notes;
    private String customerName;
    private String customerPhone;
    private List<BackofficeOrderItemDTO> items;
}
