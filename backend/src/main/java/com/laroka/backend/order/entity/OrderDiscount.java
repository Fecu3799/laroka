package com.laroka.backend.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Descuento porcentual manual aplicado a un pedido (US-19-01).
 *
 * Tabla append-only: cada aplicación inserta una fila nueva y ninguna anterior se
 * muta ni se borra. El descuento vigente es la fila más reciente por
 * {@code appliedAt}; las previas quedan como traza de auditoría de quién ajustó el
 * precio, cuándo y por qué.
 */
@Entity
@Table(name = "order_discount")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDiscount {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Porcentaje aplicado sobre el subtotal del pedido, entre 0 y 100. */
    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    /**
     * Snapshot de {@code subtotal + deliveryFee + serviceFee} al momento de aplicar.
     * Deliberadamente NO es {@code order.totalAmount}: ese valor puede venir ya
     * descontado por una aplicación previa, y encadenar descuentos sobre él haría
     * que reaplicar el mismo porcentaje diera un resultado distinto cada vez.
     */
    @Column(name = "original_total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalTotalAmount;

    /** {@code subtotal * percentage / 100}, redondeado HALF_UP a 2 decimales. */
    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    /** {@code originalTotalAmount - discountAmount}. Se copia a {@code order.totalAmount}. */
    @Column(name = "final_total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalTotalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private DiscountReason reason;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * FK a {@code staff_user}: el MANAGER o ADMIN que aplicó el descuento. Se guarda
     * como Integer plano y no como relación para no acoplar el módulo order a la
     * entidad de staffuser; la integridad la garantiza fk_order_discount_applied_by.
     */
    @Column(name = "applied_by", nullable = false)
    private Integer appliedBy;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;
}
