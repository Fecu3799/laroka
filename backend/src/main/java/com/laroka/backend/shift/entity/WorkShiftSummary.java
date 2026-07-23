package com.laroka.backend.shift.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_shift_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkShiftSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false, unique = true)
    private WorkShift shift;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders;

    @Column(name = "delivered_orders", nullable = false)
    private Integer deliveredOrders;

    @Column(name = "cancelled_orders", nullable = false)
    private Integer cancelledOrders;

    @Column(name = "total_revenue", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "cash_revenue", nullable = false, precision = 10, scale = 2)
    private BigDecimal cashRevenue;

    @Column(name = "mp_revenue", nullable = false, precision = 10, scale = 2)
    private BigDecimal mpRevenue;

    @Column(name = "qr_revenue", nullable = false, precision = 10, scale = 2)
    private BigDecimal qrRevenue;

    @Column(name = "average_ticket", nullable = false, precision = 10, scale = 2)
    private BigDecimal averageTicket;

    @Column(name = "delivery_orders", nullable = false)
    private Integer deliveryOrders;

    @Column(name = "takeaway_orders", nullable = false)
    private Integer takeawayOrders;

    @Column(name = "cancellation_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cancellationRate;

    // Descuentos del turno (US-20-02): agregados sobre los pedidos DELIVERED cuyo
    // descuento vigente es APPLIED. total_discount = suma de lo descontado;
    // discounted_orders = cuántos pedidos lo tuvieron; el resto es el desglose por
    // motivo. Persistidos con el summary para que el histórico los conserve.
    @Column(name = "total_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalDiscount;

    @Column(name = "discounted_orders", nullable = false)
    private Integer discountedOrders;

    @Column(name = "discount_customer_promo", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountCustomerPromo;

    @Column(name = "discount_transfer_adjustment", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountTransferAdjustment;

    @Column(name = "discount_other", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountOther;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    @PrePersist
    protected void onPersist() {
        calculatedAt = OffsetDateTime.now();
    }
}
