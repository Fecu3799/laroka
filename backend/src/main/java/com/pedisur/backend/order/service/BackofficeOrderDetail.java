package com.pedisur.backend.order.service;

import java.util.List;

import com.pedisur.backend.order.entity.Order;
import com.pedisur.backend.order.entity.OrderStatusHistory;
import com.pedisur.backend.payment.entity.Payment;

/**
 * {@code discount} es el descuento vigente del pedido (US-19-03) o null si nunca se
 * le aplicó ninguno.
 */
public record BackofficeOrderDetail(Order order, Payment payment, List<OrderStatusHistory> history,
                                    AppliedDiscount discount) {}
