package com.pedisur.backend.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    RECEIVED,
    IN_PREPARATION,
    ON_THE_WAY,
    READY_FOR_PICKUP,
    DELIVERED,
    CANCELLATION_REQUESTED,
    CANCELLED
}
