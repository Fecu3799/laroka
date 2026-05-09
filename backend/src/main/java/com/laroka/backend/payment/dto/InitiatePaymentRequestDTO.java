package com.laroka.backend.payment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitiatePaymentRequestDTO {

    @NotNull
    private UUID orderId;

    private BackUrls backUrls;

    @Data
    public static class BackUrls {
        private String success;
        private String failure;
        private String pending;
    }
}
