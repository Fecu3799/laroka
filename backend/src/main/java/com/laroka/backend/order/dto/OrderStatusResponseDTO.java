package com.laroka.backend.order.dto;

import java.math.BigDecimal;
import java.util.List;

import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.payment.entity.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusResponseDTO {

    private OrderStatus status;
    private PaymentStatus paymentStatus;
    // Método de pago del pedido. Lo consume el client para decidir si mostrar el aviso
    // de comisión por cancelación tardía (solo aplica a MERCADOPAGO; US-17-CF-02).
    private PaymentMethod paymentMethod;
    private OrderType orderType;
    private String branchName;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private List<OrderStatusHistoryDTO> history;
}
