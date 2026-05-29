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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.branch.entity.BranchQR;
import com.laroka.backend.branch.exception.BranchQRNotFoundException;
import com.laroka.backend.branch.repository.BranchQRRepository;
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
    private final BranchQRRepository branchQrRepository;

    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    @Transactional
    public String initiatePayment(UUID orderId, PaymentGateway.BackUrls backUrls) {
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

        String paymentLink = paymentGateway.createPreference(orderId, order.getTotalAmount(), backUrls);
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
        log.info("processWebhook: type={}, dataId={}, requestId={}", event.getType(), dataId, xRequestId);

        if (!"payment".equals(event.getType())) {
            log.warn("processWebhook: ignored event type={}, requestId={}", event.getType(), xRequestId);
            return;
        }

        String paymentId = String.valueOf(dataId);

        validateWebhookSignature(xSignature, xRequestId, paymentId);

        Optional<Payment> existingByPaymentId = paymentRepository.findByMercadopagoPaymentId(paymentId);
        if (existingByPaymentId.isPresent()
                && existingByPaymentId.get().getStatus() != PaymentStatus.PENDING) {
            log.warn("processWebhook: duplicate webhook ignored — paymentId={}, currentStatus={}, requestId={}",
                    paymentId, existingByPaymentId.get().getStatus(), xRequestId);
            return;
        }

        PaymentGateway.PaymentInfo info = paymentGateway.fetchPayment(paymentId);
        log.info("processWebhook: fetchPayment result — status={}, externalReference={}, requestId={}",
                info.status(), info.externalReference(), xRequestId);

        PaymentStatus newStatus = mapMpStatus(info.status());
        UUID orderId = UUID.fromString(info.externalReference());

        Payment payment = existingByPaymentId.orElseGet(() ->
                paymentRepository.findByOrderId(orderId)
                        .orElseThrow(() -> new PaymentNotFoundException(orderId)));

        log.info("processWebhook: updating payment — paymentId={}, orderId={}, newStatus={}, requestId={}",
                payment.getId(), orderId, newStatus, xRequestId);
        payment.setMercadopagoPaymentId(paymentId);
        payment.setStatus(newStatus);
        if (newStatus == PaymentStatus.APPROVED) {
            payment.setPaidAt(LocalDateTime.now());
        }
        paymentRepository.save(payment);
        log.info("processWebhook: payment saved — paymentId={}, orderId={}, newStatus={}, requestId={}",
                payment.getId(), orderId, newStatus, xRequestId);

        if (newStatus != PaymentStatus.PENDING) {
            clearQrActivePayment(orderId);
        }

        if (newStatus == PaymentStatus.APPROVED) {
            Order order = orderRepository.findByIdWithBranch(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                log.info("processWebhook: transitioning order to RECEIVED — orderId={}, requestId={}", orderId, xRequestId);
                orderService.transitionStatus(orderId, OrderStatus.RECEIVED);
                log.info("processWebhook: order transitioned to RECEIVED — orderId={}, requestId={}", orderId, xRequestId);
                notificationService.sendNewOrderEvent(order.getBranch().getId(), orderId, order.getCreatedAt());
            } else {
                log.warn("processWebhook: order not in PENDING_PAYMENT, skipping activation — orderId={}, orderStatus={}, requestId={}",
                        orderId, order.getStatus(), xRequestId);
            }
        }
    }

    @Transactional
    public Payment confirmCashPayment(UUID orderId, Integer branchId) {
        Order order = orderRepository.findByIdWithBranch(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getBranch().getId().equals(branchId)) {
            log.warn("Branch mismatch on cash payment confirm | orderId={} orderBranch={} userBranch={}",
                    orderId, order.getBranch().getId(), branchId);
            throw new AccessDeniedException("El pedido no pertenece a la sucursal del usuario");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        if (payment.getMethod() != PaymentMethod.CASH) {
            throw new BusinessException("La confirmación manual solo aplica a pedidos en efectivo");
        }

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            throw new BusinessException("El pago ya fue aprobado");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException("El pago no está en estado PENDING");
        }

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        log.info("Cash payment confirmed | orderId={} paymentId={}", orderId, saved.getId());
        return saved;
    }

    @Transactional
    public void chargeQr(UUID orderId, Integer branchId) {
        BranchQR branchQr = branchQrRepository.findByBranchId(branchId)
                .orElseThrow(() -> new BranchQRNotFoundException(branchId));

        if (!branchQr.isActive()) {
            throw new BusinessException("El QR de la sucursal no está activo");
        }

        Order order = orderRepository.findByIdWithBranch(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getBranch().getId().equals(branchId)) {
            log.warn("chargeQr: branch mismatch | orderId={} orderBranch={} userBranch={}",
                    orderId, order.getBranch().getId(), branchId);
            throw new AccessDeniedException("El pedido no pertenece a la sucursal del usuario");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("El pedido no está en estado PENDING_PAYMENT");
        }

        if (branchQr.getActivePaymentId() != null) {
            log.info("chargeQr: cancelling previous active charge | externalId={} branchId={}",
                    branchQr.getActivePaymentId(), branchId);
            paymentGateway.cancelQrCharge(branchQr.getActivePaymentId());
            branchQr.setActivePaymentId(null);
        }

        String externalId = paymentGateway.chargeQr(branchQr.getMpPosId(), orderId, order.getTotalAmount());
        branchQr.setActivePaymentId(externalId);
        branchQrRepository.save(branchQr);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> Payment.builder().id(UUID.randomUUID()).order(order).build());
        payment.setMethod(PaymentMethod.QR_CODE);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        log.info("chargeQr: QR charge initiated | orderId={} branchId={} externalId={}",
                orderId, branchId, externalId);
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

    private void clearQrActivePayment(UUID orderId) {
        orderRepository.findByIdWithBranch(orderId).ifPresent(order ->
            branchQrRepository.findByBranchId(order.getBranch().getId()).ifPresent(qr -> {
                if (qr.getActivePaymentId() != null) {
                    qr.setActivePaymentId(null);
                    branchQrRepository.save(qr);
                    log.info("clearQrActivePayment: cleared | branchId={} orderId={}",
                            order.getBranch().getId(), orderId);
                }
            })
        );
    }

    private void validateWebhookSignature(String xSignature, String xRequestId, String dataId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("validateWebhookSignature: webhookSecret not configured — skipping validation");
            return;
        }
        if (xSignature == null || xSignature.isBlank()) {
            log.warn("validateWebhookSignature: x-signature header missing — dataId={}, requestId={}", dataId, xRequestId);
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
            log.warn("validateWebhookSignature: invalid x-signature format — header={}, dataId={}, requestId={}", xSignature, dataId, xRequestId);
            throw new BusinessException("Formato de firma del webhook inválido");
        }

        String message = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            if (!computed.equals(v1)) {
                log.warn("validateWebhookSignature: HMAC mismatch — dataId={}, requestId={}, message={}", dataId, xRequestId, message);
                throw new BusinessException("Firma del webhook inválida");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("validateWebhookSignature: unexpected error — dataId={}, requestId={}, error={}", dataId, xRequestId, e.getMessage());
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
