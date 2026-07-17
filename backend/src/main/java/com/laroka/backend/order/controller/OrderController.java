package com.laroka.backend.order.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.order.dto.CancelOrderRequestDTO;
import com.laroka.backend.order.dto.CreateOrderRequestDTO;
import com.laroka.backend.order.dto.CreateOrderResponseDTO;
import com.laroka.backend.order.dto.OrderItemStatusDTO;
import com.laroka.backend.order.dto.OrderStatusResponseDTO;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.mapper.OrderMapper;
import com.laroka.backend.order.service.BackofficeOrderRow;
import com.laroka.backend.order.service.OrderCreationResult;
import com.laroka.backend.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Public API for order creation and tracking")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;

    @PostMapping
    @Operation(summary = "Create order", description = "Creates a new order for a branch. Send X-Idempotency-Key to avoid duplicate orders on retry.")
    public ResponseEntity<CreateOrderResponseDTO> createOrder(
            @Valid @RequestBody CreateOrderRequestDTO dto,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        Order order = orderMapper.toEntity(dto);
        List<OrderItem> items = orderMapper.toItems(dto);
        OrderCreationResult result = orderService.createOrder(order, items, dto.getPaymentMethod(), idempotencyKey);

        // Evento SSE emitido tras el commit de createOrder. Solo para pedidos nuevos:
        // un hit de idempotencia (fromCache) no re-notifica. Mover esto fuera de la
        // transacción evita retener la conexión JDBC durante el send y evita emitir
        // un NEW_ORDER de un pedido que la transacción nunca llegó a persistir.
        if (!result.fromCache()) {
            Order created = result.order();
            notificationService.sendNewOrderEvent(created.getBranch().getId(), created.getId(),
                    created.getCreatedAt(), created.getOrigin());
        }

        HttpStatus status = result.fromCache() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(orderMapper.toResponseDTO(result.order()));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get order status", description = "Returns the current status, payment status, and full state history of an order.")
    public ResponseEntity<OrderStatusResponseDTO> getOrderStatus(@PathVariable UUID id) {
        Order order = orderService.findByIdWithHistory(id);
        PaymentMethod paymentMethod = orderService.findPaymentMethod(id);
        return ResponseEntity.ok(orderMapper.toStatusResponseDTO(order, order.getStatusHistory(), paymentMethod));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancels an order or submits a cancellation request. reason is optional for direct cancellation (RECEIVED), required when the order is IN_PREPARATION (results in CANCELLATION_REQUESTED). Returns 422 if cancellation is not permitted or reason is missing.")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelOrderRequestDTO body) {
        String reason = body != null ? body.getReason() : null;
        orderService.cancelOrder(id, reason);

        // Evento SSE emitido tras el commit de cancelOrder (mismo patrón que
        // BackofficeOrderController). Se relee el pedido para conocer el estado final
        // resultante y emitir el evento correcto. Hacerlo fuera de la transacción evita
        // retener la conexión JDBC durante el emitter.send() y evita notificar una
        // cancelación que luego haga rollback.
        BackofficeOrderRow row = orderService.findOrderRowById(id);
        Integer branchId = row.order().getBranch().getId();
        if (row.order().getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            notificationService.sendCancellationRequestEvent(branchId, row.order().getId());
        } else if (row.order().getStatus() == OrderStatus.CANCELLED) {
            notificationService.sendOrderUpdatedEvent(branchId,
                    orderMapper.toBackofficeResponseDTO(row.order(), row.payment()), "CLIENT");
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Get order items", description = "Returns the list of items for an order.")
    public ResponseEntity<List<OrderItemStatusDTO>> getOrderItems(@PathVariable UUID id) {
        return ResponseEntity.ok(orderMapper.toItemStatusDTOList(orderService.findItemsByOrderId(id)));
    }
}
