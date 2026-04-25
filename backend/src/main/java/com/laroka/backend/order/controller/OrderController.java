package com.laroka.backend.order.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import com.laroka.backend.order.dto.OrderStatusResponseDTO;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderStatusHistory;
import com.laroka.backend.order.mapper.OrderMapper;
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

    private static final long IDEMPOTENCY_TTL_MINUTES = 5;

    private record IdempotencyEntry(CreateOrderResponseDTO response, Instant timestamp) {}

    private final ConcurrentHashMap<String, IdempotencyEntry> idempotencyStore = new ConcurrentHashMap<>();

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @PostMapping
    @Operation(summary = "Create order", description = "Creates a new order for a branch. Send X-Idempotency-Key to avoid duplicate orders on retry.")
    public ResponseEntity<CreateOrderResponseDTO> createOrder(
            @Valid @RequestBody CreateOrderRequestDTO dto,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyEntry existing = idempotencyStore.get(idempotencyKey);
            if (existing != null) {
                Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_MINUTES, ChronoUnit.MINUTES);
                if (existing.timestamp().isAfter(cutoff)) {
                    return ResponseEntity.ok(existing.response());
                }
                idempotencyStore.remove(idempotencyKey);
            }
        }

        Order order = orderMapper.toEntity(dto);
        List<OrderItem> items = orderMapper.toItems(dto);
        Order saved = orderService.createOrder(order, items, dto.getPaymentMethod());
        CreateOrderResponseDTO response = orderMapper.toResponseDTO(saved);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.put(idempotencyKey, new IdempotencyEntry(response, Instant.now()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get order status", description = "Returns the current status and full state history of an order.")
    public ResponseEntity<OrderStatusResponseDTO> getOrderStatus(@PathVariable UUID id) {
        Order order = orderService.findById(id);
        List<OrderStatusHistory> history = orderService.getHistory(id);
        return ResponseEntity.ok(orderMapper.toStatusResponseDTO(order, history));
    }
}
