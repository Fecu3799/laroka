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

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    @PrePersist
    protected void onPersist() {
        calculatedAt = OffsetDateTime.now();
    }
}
