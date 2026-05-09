package com.laroka.backend.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.laroka.backend.payment.dto.InitiatePaymentRequestDTO;
import com.laroka.backend.payment.dto.InitiatePaymentResponseDTO;
import com.laroka.backend.payment.dto.WebhookEventDTO;
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
    @Operation(summary = "Initiate payment",
            description = "Creates a MercadoPago preference for an existing order and returns the payment link.")
    public ResponseEntity<InitiatePaymentResponseDTO> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequestDTO dto) {

        String paymentLink = paymentService.initiatePayment(dto.getOrderId());
        return ResponseEntity.ok(InitiatePaymentResponseDTO.builder()
                .paymentLink(paymentLink)
                .build());
    }

    @PostMapping("/webhook")
    @Operation(summary = "MercadoPago webhook",
            description = "Receives payment status events from MercadoPago. Only 'payment' type events are processed. (US-04-02, US-04-05, US-04-06)")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestBody WebhookEventDTO event) {

        paymentService.processWebhook(xSignature, xRequestId, dataId, event);
        return ResponseEntity.ok().build();
    }
}
