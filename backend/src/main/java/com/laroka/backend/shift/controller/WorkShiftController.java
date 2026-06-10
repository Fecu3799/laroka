package com.laroka.backend.shift.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.shift.dto.OpenShiftResponseDTO;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.service.OpenShiftResult;
import com.laroka.backend.shift.service.WorkShiftService;
import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.shared.security.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/shifts")
@RequiredArgsConstructor
@Tag(name = "Work Shifts", description = "Backoffice API for shift management")
public class WorkShiftController {

    private final WorkShiftService workShiftService;
    private final SecurityUtils securityUtils;

    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Open a work shift",
            description = "Opens a new work shift for the authenticated user's branch. " +
                    "If a shift is already open, it is automatically closed before the new one is created.")
    public ResponseEntity<OpenShiftResponseDTO> openShift(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        OpenShiftResult result = workShiftService.openShift(branchId, principal.getUserId());
        WorkShift shift = result.shift();

        return ResponseEntity.ok(OpenShiftResponseDTO.builder()
            .shiftId(shift.getId())
            .openedAt(shift.getOpenedAt())
            .branchId(shift.getBranch().getId())
            .warningPreviousShiftClosed(result.previousShiftClosed())
            .build());
    }
}
