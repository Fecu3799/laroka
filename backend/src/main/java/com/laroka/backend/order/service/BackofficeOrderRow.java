package com.laroka.backend.order.service;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.payment.entity.Payment;

public record BackofficeOrderRow(Order order, Payment payment) {}
