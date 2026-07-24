package com.pedisur.backend.payment.exception;

import java.util.UUID;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class PaymentNotFoundException extends EntityNotFoundException {
    public PaymentNotFoundException(UUID orderId) {
        super("Payment not found for order id: " + orderId);
    }
}
