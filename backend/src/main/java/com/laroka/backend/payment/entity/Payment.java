package com.laroka.backend.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.PaymentMethod;

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

@Entity
@Table(name = "payment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    private UUID id;

    @Column(name = "mercadopago_payment_id")
    private String mercadopagoPaymentId;

    @Column(name = "mercadopago_preference_id")
    private String mercadopagoPreferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // Monto reembolsado (US-17-04). Total → totalAmount del pedido; parcial → 85% del
    // subtotal. Se setea al reembolsar (éxito → junto a REFUNDED) y también al fallar
    // (junto a REFUND_FAILED = monto pendiente de reintento). null = sin reembolso.
    @Column(name = "refunded_amount", precision = 10, scale = 2)
    private BigDecimal refundedAmount;

    // Momento en que el pago entró en REFUND_FAILED (US-17-08). Lo lee el job de
    // avisos de demora para detectar reembolsos sin resolver hace más de N horas.
    @Column(name = "refund_failed_at")
    private LocalDateTime refundFailedAt;

    // Marca que el aviso de demora ya se envió al cliente: garantiza un solo aviso
    // por pedido (US-17-08). Primitivo → default false para pagos nuevos.
    @Column(name = "refund_delay_notified", nullable = false)
    private boolean refundDelayNotified;

    @Column(name = "payment_link", columnDefinition = "TEXT")
    private String paymentLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
}
