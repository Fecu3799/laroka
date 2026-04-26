package com.laroka.backend.order.dto;

import java.util.List;

import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;

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
    private OrderType orderType;
    private String branchName;
    private List<OrderStatusHistoryDTO> history;
}
