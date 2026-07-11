package com.laroka.backend.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.payment.entity.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackofficeOrderDetailDTO {

    private UUID id;
    private Long orderNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private OrderStatus status;
    private OrderType orderType;
    private OrderOrigin origin;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private String notes;
    private String customerName;
    private String customerPhone;
    private String branchName;
    private List<OrderItemResponseDTO> items;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private LocalDateTime paidAt;
    private List<OrderStatusHistoryDTO> statusHistory;
    private String cancellationReason;
}
