package com.pedisur.backend.payment.entity;

public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    REFUNDED,
    // El reembolso automático (US-17-02/03) se intentó pero el gateway falló. El
    // dinero sigue en el comercio; queda pendiente de reintento manual (US-17-05).
    REFUND_FAILED
}
