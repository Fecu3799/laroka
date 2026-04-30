package com.laroka.backend.payment.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.exception.OrderNotFoundException;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.exception.PaymentNotFoundException;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public String initiatePayment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("El pedido no está en estado PENDING_PAYMENT");
        }

        boolean alreadyActive = paymentRepository.existsByOrderIdAndStatusIn(
                orderId, List.of(PaymentStatus.PENDING, PaymentStatus.APPROVED));
        if (alreadyActive) {
            throw new BusinessException("Ya existe un pago activo para este pedido");
        }

        String paymentLink = paymentGateway.createPreference(orderId, order.getTotalAmount());

        String preferenceId = extractPreferenceId(paymentLink);

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(PaymentStatus.PENDING)
                .method(PaymentMethod.MERCADOPAGO)
                .mercadopagoPreferenceId(preferenceId)
                .build();

        paymentRepository.save(payment);
        return paymentLink;
    }

    @Transactional(readOnly = true)
    public Payment findByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private String extractPreferenceId(String paymentLink) {
        if (paymentLink.contains("pref_id=")) {
            return paymentLink.substring(paymentLink.indexOf("pref_id=") + 8);
        }
        return null;
    }
}
