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
    private OffsetDateTime calculatedAt;
}
