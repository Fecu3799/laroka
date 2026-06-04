package com.laroka.backend.order.dto;

import java.time.LocalDateTime;

import com.laroka.backend.order.entity.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistoryDTO {

    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private LocalDateTime changedAt;
    private String cancellationReason;
}
