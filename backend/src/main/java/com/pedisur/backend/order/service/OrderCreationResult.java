package com.pedisur.backend.order.service;

import com.pedisur.backend.order.entity.Order;

public record OrderCreationResult(Order order, boolean fromCache) {}
