package com.pedisur.backend.order.service;

import com.pedisur.backend.order.entity.Order;
import com.pedisur.backend.order.entity.OrderDiscount;
import com.pedisur.backend.payment.entity.Payment;

/**
 * {@code discount} es el descuento vigente del pedido (US-19-04) o null si no tiene.
 * Viaja en la fila —y no sólo en el detalle— porque el ticket se imprime desde la
 * lista sin un fetch extra, y necesita la línea de descuento para que el TOTAL
 * impreso sea explicable.
 */
public record BackofficeOrderRow(Order order, Payment payment, OrderDiscount discount) {}
