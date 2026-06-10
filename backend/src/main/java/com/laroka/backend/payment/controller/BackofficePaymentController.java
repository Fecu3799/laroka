package com.laroka.backend.payment.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.payment.dto.QrChargeRequestDTO;
import com.laroka.backend.payment.service.PaymentService;
import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.shared.security.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/payments")
@RequiredArgsConstructor
@Tag(name = "Backoffice Payments", description = "Backoffice payment operations")
public class BackofficePaymentController {

    private final PaymentService paymentService;
    private final SecurityUtils securityUtils;

    @PostMapping("/qr-charge")
    @Operation(summary = "Charge QR",
            description = "Loads the order amount onto the branch QR via MercadoPago QR Modelo Atendido. "
                    + "Cancels any active QR charge before registering the new one (RN-21).")
    public ResponseEntity<Void> chargeQr(
            @Valid @RequestBody QrChargeRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        UUID orderId = dto.getOrderId();
        Integer branchId = securityUtils.resolveBranchId(principal, request);
        paymentService.chargeQr(orderId, branchId);
        return ResponseEntity.ok().build();
    }
}
