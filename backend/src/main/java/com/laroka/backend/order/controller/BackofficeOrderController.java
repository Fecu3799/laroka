package com.laroka.backend.order.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.order.dto.ApplyDiscountRequestDTO;
import com.laroka.backend.order.dto.BackofficeOrderDetailDTO;
import com.laroka.backend.order.dto.BackofficeOrderPageDTO;
import com.laroka.backend.order.dto.BackofficeOrderResponseDTO;
import com.laroka.backend.order.dto.CancelRequestActionDTO;
import com.laroka.backend.order.dto.OrderFilterParams;
import com.laroka.backend.order.dto.UpdateOrderStatusRequestDTO;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.mapper.OrderMapper;
import com.laroka.backend.order.service.BackofficeOrderDetail;
import com.laroka.backend.order.service.BackofficeOrderRow;
import com.laroka.backend.order.service.OrderService;
import com.laroka.backend.payment.dto.PaymentStatusResponseDTO;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.service.PaymentService;
import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.shared.exception.BusinessException;
import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.shared.security.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/orders")
@RequiredArgsConstructor
@Tag(name = "Backoffice Orders", description = "Backoffice API for order management")
public class BackofficeOrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "Get active orders",
            description = "Returns active orders for the authenticated user's branch. " +
                    "Supports optional filters: status, dateFrom, dateTo, orderBy (createdAt_asc | createdAt_desc, default: createdAt_desc). " +
                    "When shiftId is present, orders are filtered by shift instead of dateFrom/dateTo.")
    public ResponseEntity<List<BackofficeOrderResponseDTO>> getActiveOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false, defaultValue = "createdAt_desc") String orderBy,
            @RequestParam(required = false) UUID shiftId,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        OrderFilterParams params = new OrderFilterParams(status, dateFrom, dateTo, orderBy);
        List<BackofficeOrderRow> rows = orderService.findActiveOrdersByBranch(branchId, shiftId, params);
        List<BackofficeOrderResponseDTO> response = rows.stream()
                .map(row -> orderMapper.toBackofficeResponseDTO(row.order(), row.payment(), row.discount()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Get order history",
            description = "Returns paginated DELIVERED and CANCELLED orders for the authenticated user's branch. " +
                    "Optional filters: status (DELIVERED|CANCELLED), dateFrom, dateTo (ISO 8601). " +
                    "Default: page=0, size=20, sorted by createdAt DESC.")
    public ResponseEntity<BackofficeOrderPageDTO> getOrderHistory(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        Page<BackofficeOrderRow> orderPage = orderService.findOrderHistoryByBranch(
                branchId, status, dateFrom, dateTo, page, size);

        List<BackofficeOrderResponseDTO> content = orderPage.getContent().stream()
                .map(row -> orderMapper.toBackofficeResponseDTO(row.order(), row.payment(), row.discount()))
                .toList();

        return ResponseEntity.ok(BackofficeOrderPageDTO.builder()
                .content(content)
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order detail",
            description = "Returns full detail of an order including items, payment, and status history. " +
                    "Returns 403 if order belongs to a different branch, 404 if not found.")
    public ResponseEntity<BackofficeOrderDetailDTO> getOrderDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        BackofficeOrderDetail detail = orderService.getOrderDetailForBackoffice(id, branchId);
        return ResponseEntity.ok(orderMapper.toBackofficeDetailDTO(detail));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status",
            description = "Transitions an order to the next status. Validates via OrderStateMachine. " +
                    "Returns 422 on invalid transition, 403 if order belongs to a different branch.")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        orderService.transitionStatusForBackoffice(id, dto.getNextStatus(), dto.getReason(),
                branchId, principal.getUserId());
        BackofficeOrderRow updatedRow = orderService.findOrderRowById(id);
        notificationService.sendOrderUpdatedEvent(branchId,
                orderMapper.toBackofficeResponseDTO(updatedRow.order(), updatedRow.payment(), updatedRow.discount()), "BACKOFFICE");
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status/previous")
    @Operation(summary = "Revert order to previous status",
            description = "Moves an order back one step. Only allowed from IN_PREPARATION, ON_THE_WAY, and READY_FOR_PICKUP. " +
                    "Returns 422 if the current status cannot be reverted, 403 if order belongs to a different branch.")
    public ResponseEntity<Void> revertStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        orderService.transitionToPreviousStatusForBackoffice(id, branchId, principal.getUserId());
        BackofficeOrderRow updatedRow = orderService.findOrderRowById(id);
        notificationService.sendOrderUpdatedEvent(branchId,
                orderMapper.toBackofficeResponseDTO(updatedRow.order(), updatedRow.payment(), updatedRow.discount()), "BACKOFFICE");
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel-request")
    @Operation(summary = "Resolve cancellation request",
            description = "Approves or rejects a cancellation request. action=APPROVE → CANCELLED, action=REJECT → IN_PREPARATION. " +
                    "Returns 422 if order is not in CANCELLATION_REQUESTED, 403 if order belongs to a different branch.")
    public ResponseEntity<Void> resolveCancellationRequest(
            @PathVariable UUID id,
            @Valid @RequestBody CancelRequestActionDTO dto,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        orderService.resolveCancellationRequest(id, dto.getAction(), branchId, principal.getUserId());
        BackofficeOrderRow updatedRow = orderService.findOrderRowById(id);
        notificationService.sendOrderUpdatedEvent(branchId,
                orderMapper.toBackofficeResponseDTO(updatedRow.order(), updatedRow.payment(), updatedRow.discount()), "BACKOFFICE");
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/payment")
    @Operation(summary = "Confirm cash payment",
            description = "Marks a CASH payment as APPROVED. Only valid when method=CASH and status=PENDING. " +
                    "Body: { \"action\": \"CONFIRM\" }. Returns 422 if already approved or not a cash payment, 403 if wrong branch.")
    public ResponseEntity<PaymentStatusResponseDTO> confirmCashPayment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        if (!"CONFIRM".equals(body.get("action"))) {
            throw new BusinessException("action debe ser CONFIRM");
        }

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        Payment payment = paymentService.confirmCashPayment(id, branchId);
        BackofficeOrderRow updatedRow = orderService.findOrderRowById(id);
        notificationService.sendOrderUpdatedEvent(branchId,
                orderMapper.toBackofficeResponseDTO(updatedRow.order(), payment, updatedRow.discount()), "BACKOFFICE");
        return ResponseEntity.ok(PaymentStatusResponseDTO.builder()
                .status(payment.getStatus())
                .method(payment.getMethod())
                .paidAt(payment.getPaidAt())
                .build());
    }

    @PostMapping("/{id}/retry-refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retry a failed refund (ADMIN only)",
            description = "Retries a refund that previously failed automatically (Payment in REFUND_FAILED). " +
                    "Reintenta con el mismo monto que correspondía (total o parcial). On success the payment " +
                    "becomes REFUNDED; on repeated failure it stays REFUND_FAILED and returns an error. " +
                    "Returns 422 if the order has no pending failed refund, 403 if wrong branch or not ADMIN.")
    public ResponseEntity<Void> retryRefund(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        orderService.retryRefund(id, branchId);
        BackofficeOrderRow updatedRow = orderService.findOrderRowById(id);
        notificationService.sendOrderUpdatedEvent(branchId,
                orderMapper.toBackofficeResponseDTO(updatedRow.order(), updatedRow.payment(), updatedRow.discount()), "BACKOFFICE");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/discount")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Apply a manual percentage discount (ADMIN/MANAGER only)",
            description = "Applies a percentage discount over the order subtotal and overwrites totalAmount. " +
                    "Only while the order is active (RECEIVED, IN_PREPARATION, ON_THE_WAY, READY_FOR_PICKUP) and " +
                    "charged outside the gateway: returns 422 outside that window — the payment is still pending, " +
                    "the order was already delivered (its total is billed in the shift summary) or cancelled — " +
                    "and 422 if it has a MERCADOPAGO/QR_CODE payment in PENDING or APPROVED. " +
                    "Every application is recorded as a new order_discount row (append-only audit trail). " +
                    "Returns 403 if wrong branch or not ADMIN/MANAGER.")
    public ResponseEntity<Void> applyDiscount(
            @PathVariable UUID id,
            @Valid @RequestBody ApplyDiscountRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest request) {

        Integer branchId = securityUtils.resolveBranchId(principal, request);
        orderService.applyDiscount(id, branchId, dto.getPercentage(), dto.getReason(),
                dto.getNote(), principal.getUserId());
        BackofficeOrderRow updatedRow = orderService.findOrderRowById(id);
        notificationService.sendOrderUpdatedEvent(branchId,
                orderMapper.toBackofficeResponseDTO(updatedRow.order(), updatedRow.payment(), updatedRow.discount()), "BACKOFFICE");
        return ResponseEntity.noContent().build();
    }
}
