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

import com.laroka.backend.order.dto.CreateOrderRequestDTO;
import com.laroka.backend.order.dto.CreateOrderResponseDTO;
import com.laroka.backend.order.dto.OrderItemStatusDTO;
import com.laroka.backend.order.dto.OrderStatusResponseDTO;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.mapper.OrderMapper;
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

    @PostMapping
    @Operation(summary = "Create order", description = "Creates a new order for a branch. Send X-Idempotency-Key to avoid duplicate orders on retry.")
    public ResponseEntity<CreateOrderResponseDTO> createOrder(
            @Valid @RequestBody CreateOrderRequestDTO dto,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        Order order = orderMapper.toEntity(dto);
        List<OrderItem> items = orderMapper.toItems(dto);
        OrderCreationResult result = orderService.createOrder(order, items, dto.getPaymentMethod(), idempotencyKey);

        HttpStatus status = result.fromCache() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(orderMapper.toResponseDTO(result.order()));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get order status", description = "Returns the current status and full state history of an order.")
    public ResponseEntity<OrderStatusResponseDTO> getOrderStatus(@PathVariable UUID id) {
        Order order = orderService.findByIdWithHistory(id);
        return ResponseEntity.ok(orderMapper.toStatusResponseDTO(order, order.getStatusHistory()));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Get order items", description = "Returns the list of items for an order.")
    public ResponseEntity<List<OrderItemStatusDTO>> getOrderItems(@PathVariable UUID id) {
        return ResponseEntity.ok(orderMapper.toItemStatusDTOList(orderService.findItemsByOrderId(id)));
    }
}
