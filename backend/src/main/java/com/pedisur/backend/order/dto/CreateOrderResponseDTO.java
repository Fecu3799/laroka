package com.pedisur.backend.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.pedisur.backend.order.entity.OrderStatus;
import com.pedisur.backend.order.entity.OrderType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponseDTO {

    private UUID orderId;
    private OrderStatus status;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private OrderType orderType;
    private String branchName;
    private List<OrderItemResponseDTO> items;
}
