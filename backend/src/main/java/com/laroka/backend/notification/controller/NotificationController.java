package com.laroka.backend.notification.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.shared.security.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "SSE event stream for backoffice")
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    @GetMapping("/backoffice/events")
    @Operation(summary = "Subscribe to SSE events",
            description = "Streams real-time order events for the authenticated staff's branch. (US-05-03)")
    public SseEmitter subscribe(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {
        Integer branchId = securityUtils.resolveBranchId(principal, request);
        return notificationService.subscribe(branchId);
    }
}
