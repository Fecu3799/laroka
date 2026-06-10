package com.laroka.backend.shift.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.shift.dto.CloseShiftResponseDTO;
import com.laroka.backend.shift.dto.CurrentShiftResponseDTO;
import com.laroka.backend.shift.dto.OpenShiftResponseDTO;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.entity.WorkShiftSummary;
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

    @GetMapping("/current")
    @Operation(summary = "Get current active shift",
            description = "Returns the active shift for the authenticated user's branch, or active=false if none exists.")
    public ResponseEntity<CurrentShiftResponseDTO> getCurrentShift(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        Optional<WorkShift> shift = workShiftService.getCurrentShift(branchId);

        CurrentShiftResponseDTO response = shift.map(ws -> CurrentShiftResponseDTO.builder()
                .active(true)
                .shiftId(ws.getId())
                .openedAt(ws.getOpenedAt())
                .openedBy(ws.getOpenedBy().getName())
                .build())
            .orElse(CurrentShiftResponseDTO.builder().active(false).build());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Close the active work shift",
            description = "Closes the currently open shift for the authenticated user's branch " +
                    "and returns the calculated summary. Returns 422 if no active shift exists.")
    public ResponseEntity<CloseShiftResponseDTO> closeShift(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        WorkShiftSummary summary = workShiftService.closeShift(branchId, principal.getUserId());

        return ResponseEntity.ok(CloseShiftResponseDTO.builder()
            .shiftId(summary.getShift().getId())
            .totalOrders(summary.getTotalOrders())
            .deliveredOrders(summary.getDeliveredOrders())
            .cancelledOrders(summary.getCancelledOrders())
            .totalRevenue(summary.getTotalRevenue())
            .cashRevenue(summary.getCashRevenue())
            .mpRevenue(summary.getMpRevenue())
            .qrRevenue(summary.getQrRevenue())
            .averageTicket(summary.getAverageTicket())
            .calculatedAt(summary.getCalculatedAt())
            .build());
    }
}
