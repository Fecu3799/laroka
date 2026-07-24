package com.pedisur.backend.order.entity;

/**
 * Motivo por el que se aplicó un descuento manual a un pedido (US-19-01).
 * Es un campo de trazabilidad: no altera el cálculo, solo explica la decisión
 * comercial detrás de él al reconciliar caja. Las etiquetas visibles en español
 * las resuelve el backoffice; acá solo viaja la constante.
 */
public enum DiscountReason {
    /** Promoción o cortesía otorgada al cliente. */
    CUSTOMER_PROMO,
    /** Ajuste por cobro fuera del gateway (transferencia sin comisión). */
    TRANSFER_ADJUSTMENT,
    /** Cualquier otro motivo; se espera que venga acompañado de una nota. */
    OTHER
}
