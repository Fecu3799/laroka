package com.pedisur.backend.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedisur.backend.notification.dto.BugReportRequestDTO;
import com.pedisur.backend.notification.service.BugReportService;
import com.pedisur.backend.shared.security.CustomUserDetails;
import com.pedisur.backend.shared.security.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * US-17-07: recepción de reportes de bugs desde el backoffice. Cualquier rol
 * autenticado (ADMIN/MANAGER/STAFF) — la ruta /backoffice/** ya exige autenticación.
 */
@RestController
@RequestMapping("/backoffice/bug-reports")
@RequiredArgsConstructor
@Tag(name = "Bug Reports", description = "Envío de reportes de bugs por email (US-17-07)")
public class BugReportController {

    private final BugReportService bugReportService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Reportar un bug",
            description = "Arma un email con la identidad del operador (del JWT), la sucursal activa, la " +
                    "descripción, la URL y el user agent, y lo envía a la casilla configurada (BUG_REPORT_EMAIL). " +
                    "No persiste nada. Retorna 200 si el envío fue aceptado; 502 si el proveedor de email falló.")
    public ResponseEntity<Void> reportBug(
            @Valid @RequestBody BugReportRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        bugReportService.report(
                principal.getUserId(), branchId, dto.getDescription(), dto.getUrl(),
                dto.getUserAgent(), dto.getScreenshotUrl());
        return ResponseEntity.ok().build();
    }
}
