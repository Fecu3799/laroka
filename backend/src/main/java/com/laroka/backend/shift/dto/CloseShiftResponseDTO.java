package com.laroka.backend.shift.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloseShiftResponseDTO {

    private UUID shiftId;
    private Integer totalOrders;
    private Integer deliveredOrders;
    private Integer cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal cashRevenue;
    private BigDecimal mpRevenue;
    private BigDecimal qrRevenue;
    private BigDecimal averageTicket;
    private Integer deliveryOrders;
    private Integer takeawayOrders;
    private BigDecimal cancellationRate;
    // Descuentos del turno (US-20-02): total descontado, cantidad de pedidos con
    // descuento vigente, y desglose por motivo. El frontend oculta la sección del PDF
    // cuando discountedOrders == 0 (sin descuentos, sin ruido visual).
    private BigDecimal totalDiscount;
    private Integer discountedOrders;
    private BigDecimal discountCustomerPromo;
    private BigDecimal discountTransferAdjustment;
    private BigDecimal discountOther;
    private OffsetDateTime calculatedAt;
    // true si el turno se cerró automáticamente (por duración máxima, sin cierre
    // manual). Derivado de closedBy == null en un turno ya CLOSED (US-16-04).
    private boolean autoClose;
}
