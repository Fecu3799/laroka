package com.pedisur.backend.order.entity;

/**
 * Naturaleza de una fila de {@code order_discount} (US-19-06).
 *
 * La tabla es append-only: en vez de borrar un descuento, se inserta una fila que
 * lo revierte. El descuento vigente es la fila más reciente; si su acción es
 * {@link #REVERTED}, el pedido no tiene descuento real aunque la traza aplicado ->
 * revertido siga en la tabla.
 */
public enum DiscountAction {
    /** El descuento se aplicó (o se modificó reaplicando, US-19-05). */
    APPLIED,
    /** El descuento vigente se revirtió: el pedido vuelve a su total sin descontar. */
    REVERTED
}
