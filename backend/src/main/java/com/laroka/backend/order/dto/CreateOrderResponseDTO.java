package com.laroka.backend.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;

import lombok.Data;

@Data
public class CreateOrderResponseDTO {

    private UUID orderId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private OrderType orderType;
    private String branchName;
    private List<OrderItemResponseDTO> items;
}
