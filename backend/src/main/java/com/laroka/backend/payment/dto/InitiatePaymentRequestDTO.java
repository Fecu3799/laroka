package com.laroka.backend.payment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitiatePaymentRequestDTO {

    @NotNull
    private UUID orderId;
}
