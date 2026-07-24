package com.pedisur.backend.notification.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedisur.backend.notification.dto.PushSubscriptionRequestDTO;
import com.pedisur.backend.notification.dto.PushSubscriptionResponseDTO;
import com.pedisur.backend.notification.dto.PushUnsubscribeRequestDTO;
import com.pedisur.backend.notification.service.PushSubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
@Tag(name = "Push Subscriptions", description = "Web Push subscription management for anonymous clients (US-09-01)")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    @PostMapping("/subscribe")
    @Operation(summary = "Subscribe to Web Push",
            description = "Public endpoint. Registers (or refreshes) a Web Push subscription for an anonymous device.")
    public ResponseEntity<PushSubscriptionResponseDTO> subscribe(@Valid @RequestBody PushSubscriptionRequestDTO dto) {
        UUID id = pushSubscriptionService.upsert(dto.getEndpoint(), dto.getP256dh(), dto.getAuth());
        return ResponseEntity.ok(PushSubscriptionResponseDTO.builder().id(id).build());
    }

    @DeleteMapping("/subscribe")
    @Operation(summary = "Unsubscribe from Web Push",
            description = "Public endpoint. Removes a Web Push subscription by endpoint and unlinks its orders.")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeRequestDTO dto) {
        pushSubscriptionService.delete(dto.getEndpoint());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
