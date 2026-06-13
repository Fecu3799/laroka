package com.laroka.backend.shift.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.shared.exception.BusinessException;
import com.laroka.backend.shift.dto.CloseShiftEmptyResponseDTO;
import com.laroka.backend.shift.dto.CloseShiftResponseDTO;
import com.laroka.backend.shift.dto.CurrentShiftResponseDTO;
import com.laroka.backend.shift.dto.OpenShiftResponseDTO;
import com.laroka.backend.shift.dto.ShiftHistoryItemDTO;
import com.laroka.backend.shift.dto.TopProductDTO;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.entity.WorkShiftSummary;
import com.laroka.backend.shift.service.CloseShiftResult;
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

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Get closed shift history",
            description = "Returns a paginated list of closed shifts with their summaries, ordered by closedAt descending.")
    public ResponseEntity<Page<ShiftHistoryItemDTO>> getShiftHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        Page<WorkShift> shifts = workShiftService.getShiftHistory(branchId, page, size);
        return ResponseEntity.ok(shifts.map(this::toHistoryItem));
    }

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
            description = "Closes the currently open shift for the authenticated user's branch. " +
                    "Returns 200 with the calculated summary, or 200 with { empty: true } if the " +
                    "shift had no activity and was deleted. Returns 422 if no active shift exists.")
    public ResponseEntity<?> closeShift(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        CloseShiftResult result = workShiftService.closeShift(branchId, principal.getUserId());

        if (result.wasEmpty()) {
            return ResponseEntity.ok(new CloseShiftEmptyResponseDTO(true,
                "El turno no registró actividad y fue eliminado"));
        }

        WorkShiftSummary summary = result.summary();
        return ResponseEntity.ok(toCloseShiftResponse(summary.getShift().getId(), summary));
    }

    @GetMapping("/current/summary")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Get live summary of current active shift",
            description = "Returns a calculated (non-persisted) summary of the currently open shift. Returns 404 if no active shift exists.")
    public ResponseEntity<CloseShiftResponseDTO> getCurrentShiftSummary(
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        try {
            WorkShiftSummary summary = workShiftService.getCurrentShiftSummary(branchId);
            return ResponseEntity.ok(toCloseShiftResponse(summary.getShift().getId(), summary));
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{shiftId}/top-products")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Get top 5 products sold in a shift",
            description = "Returns the 5 most sold products (by quantity) for the given shift, calculated on-demand from DELIVERED orders.")
    public ResponseEntity<List<TopProductDTO>> getTopProducts(
            @PathVariable UUID shiftId,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        return ResponseEntity.ok(workShiftService.getTopProducts(shiftId, branchId));
    }

    private ShiftHistoryItemDTO toHistoryItem(WorkShift ws) {
        WorkShiftSummary s = ws.getSummary();
        return ShiftHistoryItemDTO.builder()
            .shiftId(ws.getId())
            .openedAt(ws.getOpenedAt())
            .closedAt(ws.getClosedAt())
            .openedBy(ws.getOpenedBy().getName())
            .closedBy(ws.getClosedBy() != null ? ws.getClosedBy().getName() : null)
            .summary(toCloseShiftResponse(ws.getId(), s))
            .build();
    }

    private CloseShiftResponseDTO toCloseShiftResponse(UUID shiftId, WorkShiftSummary s) {
        return CloseShiftResponseDTO.builder()
            .shiftId(shiftId)
            .totalOrders(s.getTotalOrders())
            .deliveredOrders(s.getDeliveredOrders())
            .cancelledOrders(s.getCancelledOrders())
            .totalRevenue(s.getTotalRevenue())
            .cashRevenue(s.getCashRevenue())
            .mpRevenue(s.getMpRevenue())
            .qrRevenue(s.getQrRevenue())
            .averageTicket(s.getAverageTicket())
            .deliveryOrders(s.getDeliveryOrders())
            .takeawayOrders(s.getTakeawayOrders())
            .cancellationRate(s.getCancellationRate())
            .calculatedAt(s.getCalculatedAt())
            .build();
    }
}
