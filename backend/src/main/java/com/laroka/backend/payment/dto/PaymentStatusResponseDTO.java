package com.laroka.backend.payment.dto;

import java.time.LocalDateTime;

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
public class PaymentStatusResponseDTO {

    private PaymentStatus status;
    private PaymentMethod method;
    private LocalDateTime paidAt;
}
