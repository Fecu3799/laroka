package com.laroka.backend.order.service;

import static com.laroka.backend.order.entity.OrderStatus.CANCELLED;
import static com.laroka.backend.order.entity.OrderStatus.PENDING_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatusHistory;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderExpirationService service;

    // --- helpers ---

    private Order pendingOrder() {
        return Order.builder().id(UUID.randomUUID()).status(PENDING_PAYMENT).build();
    }

    // --- no expired orders ---

    @Test
    void cancelExpiredOrders_noExpiredOrders_returnsZero() {
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(PENDING_PAYMENT), any()))
                .thenReturn(List.of());

        int count = service.cancelExpiredOrders(30);

        assertThat(count).isZero();
        verify(orderRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    // --- expired order without APPROVED payment ---

    @Test
    void cancelExpiredOrders_expiredOrderWithoutApprovedPayment_cancelledAndHistoryRecorded() {
        Order order = pendingOrder();
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any()))
                .thenReturn(false);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = service.cancelExpiredOrders(30);

        assertThat(count).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(CANCELLED);
        verify(orderRepository).save(order);
        verify(historyRepository).save(any(OrderStatusHistory.class));
    }

    // --- expired order WITH APPROVED payment (webhook already processed) ---

    @Test
    void cancelExpiredOrders_expiredOrderWithApprovedPayment_skipped() {
        Order order = pendingOrder();
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(order.getId()), any()))
                .thenReturn(true);

        int count = service.cancelExpiredOrders(30);

        assertThat(count).isZero();
        assertThat(order.getStatus()).isEqualTo(PENDING_PAYMENT);
        verify(orderRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    // --- mixed batch ---

    @Test
    void cancelExpiredOrders_mixedBatch_cancelsOnlyOrdersWithoutApprovedPayment() {
        Order toCancel = pendingOrder();
        Order toSkip = pendingOrder();

        when(orderRepository.findByStatusAndCreatedAtBefore(eq(PENDING_PAYMENT), any()))
                .thenReturn(List.of(toCancel, toSkip));
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(toCancel.getId()), any()))
                .thenReturn(false);
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(toSkip.getId()), any()))
                .thenReturn(true);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = service.cancelExpiredOrders(30);

        assertThat(count).isEqualTo(1);
        assertThat(toCancel.getStatus()).isEqualTo(CANCELLED);
        assertThat(toSkip.getStatus()).isEqualTo(PENDING_PAYMENT);
    }
}
