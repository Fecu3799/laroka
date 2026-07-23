package com.laroka.backend.order.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.order.dto.BackofficeOrderResponseDTO;
import com.laroka.backend.order.dto.CreateOrderRequestDTO;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.mapper.OrderMapper;
import com.laroka.backend.order.service.BackofficeOrderRow;
import com.laroka.backend.order.service.OrderCreationResult;
import com.laroka.backend.order.service.OrderService;
import com.laroka.backend.shared.exception.BusinessException;

/**
 * Verifica que la emisión de eventos SSE quedó FUERA de la transacción de negocio:
 * el controller emite el evento solo después de que el método @Transactional del
 * service retorna con éxito. Si el service lanza (la transacción hace rollback), no
 * debe emitirse ningún evento — antes del fix el send vivía dentro de la transacción
 * y el cliente SSE recibía un evento de un pedido que nunca se persistía.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock private OrderService orderService;
    @Mock private OrderMapper orderMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private OrderController controller;

    private Order orderWith(UUID id, OrderStatus status) {
        return Order.builder()
                .id(id)
                .status(status)
                .branch(Branch.builder().id(1).build())
                .origin(OrderOrigin.CLIENT)
                .createdAt(LocalDateTime.of(2026, 6, 28, 12, 0))
                .build();
    }

    // --- createOrder ---

    @Test
    void createOrder_newOrder_emitsNewOrderEventAfterCommit() {
        Order created = orderWith(UUID.randomUUID(), OrderStatus.PENDING_PAYMENT);
        when(orderService.createOrder(any(), any(), any(), any()))
                .thenReturn(new OrderCreationResult(created, false));

        controller.createOrder(mock(CreateOrderRequestDTO.class), "key-new");

        verify(notificationService).sendNewOrderEvent(
                eq(created.getBranch().getId()), eq(created.getId()),
                eq(created.getCreatedAt()), eq(created.getOrigin()));
    }

    @Test
    void createOrder_idempotencyHit_doesNotEmitEvent() {
        Order cached = orderWith(UUID.randomUUID(), OrderStatus.RECEIVED);
        when(orderService.createOrder(any(), any(), any(), any()))
                .thenReturn(new OrderCreationResult(cached, true));

        controller.createOrder(mock(CreateOrderRequestDTO.class), "dup-key");

        verifyNoInteractions(notificationService);
    }

    @Test
    void createOrder_serviceRollsBack_doesNotEmitEvent() {
        when(orderService.createOrder(any(), any(), any(), any()))
                .thenThrow(new BusinessException("No hay turno activo para esta sucursal"));

        assertThatThrownBy(() -> controller.createOrder(mock(CreateOrderRequestDTO.class), "key-fail"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(notificationService);
    }

    // --- cancelOrder ---

    @Test
    void cancelOrder_resultsCancelled_emitsOrderUpdatedEvent() {
        UUID id = UUID.randomUUID();
        Order order = orderWith(id, OrderStatus.CANCELLED);
        when(orderService.findOrderRowById(id)).thenReturn(new BackofficeOrderRow(order, null, null));
        // US-19-04: el evento SSE se arma con el overload que además lleva el descuento.
        when(orderMapper.toBackofficeResponseDTO(any(), any(), any()))
                .thenReturn(new BackofficeOrderResponseDTO());

        controller.cancelOrder(id, null);

        verify(notificationService).sendOrderUpdatedEvent(eq(order.getBranch().getId()), any(), eq("CLIENT"));
        verify(notificationService, never()).sendCancellationRequestEvent(any(), any());
    }

    @Test
    void cancelOrder_resultsCancellationRequested_emitsCancellationRequestEvent() {
        UUID id = UUID.randomUUID();
        Order order = orderWith(id, OrderStatus.CANCELLATION_REQUESTED);
        when(orderService.findOrderRowById(id)).thenReturn(new BackofficeOrderRow(order, null, null));

        controller.cancelOrder(id, null);

        verify(notificationService).sendCancellationRequestEvent(order.getBranch().getId(), order.getId());
        verify(notificationService, never()).sendOrderUpdatedEvent(any(), any(), any());
    }

    @Test
    void cancelOrder_serviceRollsBack_doesNotEmitEventNorRereadOrder() {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessException("El pedido no puede cancelarse en este estado"))
                .when(orderService).cancelOrder(eq(id), any());

        assertThatThrownBy(() -> controller.cancelOrder(id, null))
                .isInstanceOf(BusinessException.class);

        verify(orderService, never()).findOrderRowById(any());
        verifyNoInteractions(notificationService);
    }
}
