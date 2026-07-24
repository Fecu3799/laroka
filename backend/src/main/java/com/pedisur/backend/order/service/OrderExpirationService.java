package com.pedisur.backend.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedisur.backend.order.entity.Order;
import com.pedisur.backend.order.entity.OrderStatus;
import com.pedisur.backend.order.entity.OrderStatusHistory;
import com.pedisur.backend.order.repository.OrderRepository;
import com.pedisur.backend.order.repository.OrderStatusHistoryRepository;
import com.pedisur.backend.payment.entity.PaymentStatus;
import com.pedisur.backend.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public int cancelExpiredOrders(long expirationMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(expirationMinutes);
        List<Order> candidates = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, threshold);

        int count = 0;
        for (Order order : candidates) {
            boolean hasApprovedPayment = paymentRepository.existsByOrderIdAndStatusIn(
                    order.getId(), List.of(PaymentStatus.APPROVED));
            if (!hasApprovedPayment) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                historyRepository.save(OrderStatusHistory.builder()
                        .id(UUID.randomUUID())
                        .order(order)
                        .fromStatus(OrderStatus.PENDING_PAYMENT)
                        .toStatus(OrderStatus.CANCELLED)
                        .changedAt(LocalDateTime.now())
                        .build());
                count++;
            }
        }
        return count;
    }
}
