package com.laroka.backend.order.service;

import com.laroka.backend.order.entity.Order;

public record OrderCreationResult(Order order, boolean fromCache) {}
