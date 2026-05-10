package com.laroka.backend.order.service;

import java.util.List;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatusHistory;
import com.laroka.backend.payment.entity.Payment;

public record BackofficeOrderDetail(Order order, Payment payment, List<OrderStatusHistory> history) {}
