package com.laroka.backend.payment.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.exception.OrderNotFoundException;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.service.OrderService;
import com.laroka.backend.payment.dto.WebhookEventDTO;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.exception.PaymentNotFoundException;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    @Transactional
    public String initiatePayment(UUID orderId) {
        log.info("initiatePayment: orderId={}, totalAmount will be resolved from order", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        log.info("initiatePayment: orderId={}, totalAmount={}", orderId, order.getTotalAmount());

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
        log.info("initiatePayment: payment created and saved — orderId={}, preferenceId={}", orderId, preferenceId);
        return paymentLink;
    }

    @Transactional
    public void processWebhook(String xSignature, String xRequestId, String dataId, WebhookEventDTO event) {
        log.info("processWebhook: type={}, dataId={}", event.getType(), dataId);

        if (!"payment".equals(event.getType())) {
            log.warn("processWebhook: ignored event type={}", event.getType());
            return;
        }

        String paymentId = String.valueOf(dataId);

        validateWebhookSignature(xSignature, xRequestId, paymentId);

        Optional<Payment> existingByPaymentId = paymentRepository.findByMercadopagoPaymentId(paymentId);
        if (existingByPaymentId.isPresent()
                && existingByPaymentId.get().getStatus() != PaymentStatus.PENDING) {
            log.warn("processWebhook: duplicate webhook ignored — paymentId={}, currentStatus={}", paymentId, existingByPaymentId.get().getStatus());
            return;
        }

        PaymentGateway.PaymentInfo info = paymentGateway.fetchPayment(paymentId);
        log.info("processWebhook: fetchPayment result — status={}, externalReference={}", info.status(), info.externalReference());

        PaymentStatus newStatus = mapMpStatus(info.status());
        UUID orderId = UUID.fromString(info.externalReference());

        Payment payment = existingByPaymentId.orElseGet(() ->
                paymentRepository.findByOrderId(orderId)
                        .orElseThrow(() -> new PaymentNotFoundException(orderId)));

        log.info("processWebhook: updating payment — paymentId={}, newStatus={}", payment.getId(), newStatus);
        payment.setMercadopagoPaymentId(paymentId);
        payment.setStatus(newStatus);
        if (newStatus == PaymentStatus.APPROVED) {
            payment.setPaidAt(LocalDateTime.now());
        }
        paymentRepository.save(payment);
        log.info("processWebhook: payment updated — paymentId={}, newStatus={}", payment.getId(), newStatus);

        if (newStatus == PaymentStatus.APPROVED) {
            Order order = orderRepository.findByIdWithBranch(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                log.info("processWebhook: transitioning order to RECEIVED — orderId={}", orderId);
                orderService.transitionStatus(orderId, OrderStatus.RECEIVED);
                log.info("processWebhook: order transitioned to RECEIVED — orderId={}", orderId);
                notificationService.sendNewOrderEvent(order.getBranch().getId(), orderId);
            }
        }
    }

    @Transactional(readOnly = true)
    public Payment findByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findOptionalByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    private void validateWebhookSignature(String xSignature, String xRequestId, String dataId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return;
        }
        if (xSignature == null || xSignature.isBlank()) {
            throw new BusinessException("Firma del webhook requerida");
        }

        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                if ("ts".equals(kv[0].trim())) {
                    ts = kv[1].trim();
                }
                if ("v1".equals(kv[0].trim())) {
                    v1 = kv[1].trim();
                }
            }
        }

        if (ts == null || v1 == null) {
            throw new BusinessException("Formato de firma del webhook inválido");
        }

        String message = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            if (!computed.equals(v1)) {
                throw new BusinessException("Firma del webhook inválida");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Error al validar la firma del webhook: " + e.getMessage());
        }
    }

    private PaymentStatus mapMpStatus(String mpStatus) {
        if (mpStatus == null) {
            return PaymentStatus.PENDING;
        }
        return switch (mpStatus.toLowerCase()) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.PENDING;
        };
    }

    private String extractPreferenceId(String paymentLink) {
        if (paymentLink.contains("pref_id=")) {
            return paymentLink.substring(paymentLink.indexOf("pref_id=") + 8);
        }
        return null;
    }
}
