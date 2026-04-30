package com.laroka.backend.order.dto;

import java.math.BigDecimal;
import java.util.List;

import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
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
    private OrderType orderType;
    private String branchName;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private List<OrderStatusHistoryDTO> history;
}
