package com.laroka.backend.notification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.shared.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "SSE event stream for backoffice")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/backoffice/events")
    @Operation(summary = "Subscribe to SSE events",
            description = "Streams real-time order events for the authenticated staff's branch. (US-05-03)")
    public SseEmitter subscribe(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {
        return notificationService.subscribe(resolveBranchId(principal, request));
    }

    /**
     * Resuelve el branchId del suscriptor SSE SIN tocar la base de datos. El stream SSE
     * es de larga duración: cualquier query acá (p. ej. el existsByIdAndTenantId de
     * SecurityUtils.resolveBranchId) retiene una conexión JDBC mientras la conexión SSE
     * siga viva, agotando el pool de Hikari. El JWT ya fue validado por
     * JwtAuthenticationFilter, así que el branchId del token (MANAGER/STAFF) y el tenant
     * del ADMIN son confiables; por eso se omite la validación branch↔tenant.
     */
    private Integer resolveBranchId(CustomUserDetails principal, HttpServletRequest request) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            return principal.getBranchId();
        }
        String headerVal = request.getHeader("X-Branch-Id");
        if (headerVal == null || headerVal.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "X-Branch-Id header is required for ADMIN role");
        }
        try {
            return Integer.parseInt(headerVal.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "X-Branch-Id header must be a valid integer");
        }
    }
}
