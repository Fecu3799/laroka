package com.pedisur.backend.order.exception;

import java.util.UUID;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class OrderNotFoundException extends EntityNotFoundException {
    public OrderNotFoundException(UUID id) {
        super("Order not found with id: " + id);
    }
}
