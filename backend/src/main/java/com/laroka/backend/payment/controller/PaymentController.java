package com.laroka.backend.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.payment.dto.InitiatePaymentRequestDTO;
import com.laroka.backend.payment.dto.InitiatePaymentResponseDTO;
import com.laroka.backend.payment.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment initiation and webhook processing")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate payment", description = "Creates a MercadoPago preference for an existing order and returns the payment link.")
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequestDTO dto) {

        String paymentLink = paymentService.initiatePayment(dto.getOrderId());
        return ResponseEntity.ok(InitiatePaymentResponseDTO.builder()
                .paymentLink(paymentLink)
                .build());
    }
}
