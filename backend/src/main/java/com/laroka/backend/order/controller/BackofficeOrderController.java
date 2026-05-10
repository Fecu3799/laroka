package com.laroka.backend.order.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.order.dto.BackofficeOrderResponseDTO;
import com.laroka.backend.order.dto.OrderFilterParams;
import com.laroka.backend.order.dto.UpdateOrderStatusRequestDTO;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.mapper.OrderMapper;
import com.laroka.backend.order.service.BackofficeOrderRow;
import com.laroka.backend.order.service.OrderService;
import com.laroka.backend.shared.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/orders")
@RequiredArgsConstructor
@Tag(name = "Backoffice Orders", description = "Backoffice API for order management")
public class BackofficeOrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @GetMapping
    @Operation(summary = "Get active orders",
            description = "Returns active orders for the authenticated user's branch. " +
                    "Supports optional filters: status, dateFrom, dateTo, orderBy (createdAt_asc | createdAt_desc, default: createdAt_desc).")
    public ResponseEntity<List<BackofficeOrderResponseDTO>> getActiveOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false, defaultValue = "createdAt_desc") String orderBy,
            @AuthenticationPrincipal CustomUserDetails principal) {

        OrderFilterParams params = new OrderFilterParams(status, dateFrom, dateTo, orderBy);
        List<BackofficeOrderRow> rows = orderService.findActiveOrdersByBranch(principal.getBranchId(), params);
        List<BackofficeOrderResponseDTO> response = rows.stream()
                .map(row -> orderMapper.toBackofficeResponseDTO(row.order(), row.payment()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status",
            description = "Transitions an order to the next status. Validates via OrderStateMachine. " +
                    "Returns 422 on invalid transition, 403 if order belongs to a different branch.")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails principal) {

        orderService.transitionStatusForBackoffice(id, dto.getNextStatus(),
                principal.getBranchId(), principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
