package com.laroka.backend.order.dto;

import java.time.LocalDateTime;

import com.laroka.backend.order.entity.OrderStatus;

public record OrderFilterParams(
        OrderStatus status,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        String orderBy
) {
    public static final String ORDER_ASC = "createdAt_asc";

    public static OrderFilterParams defaults() {
        return new OrderFilterParams(null, null, null, null);
    }

    public boolean ascending() {
        return ORDER_ASC.equals(orderBy);
    }
}
