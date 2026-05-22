package com.laroka.backend.payment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QrChargeRequestDTO {

    @NotNull(message = "orderId es obligatorio")
    private UUID orderId;
}
