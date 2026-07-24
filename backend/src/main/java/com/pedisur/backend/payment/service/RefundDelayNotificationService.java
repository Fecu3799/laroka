package com.pedisur.backend.payment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedisur.backend.notification.service.PushNotificationService;
import com.pedisur.backend.order.entity.Order;
import com.pedisur.backend.payment.entity.Payment;
import com.pedisur.backend.payment.entity.PaymentStatus;
import com.pedisur.backend.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisa al cliente cuando el reembolso de su pedido cancelado quedó en
 * REFUND_FAILED sin resolverse hace más de N horas (US-17-08).
 *
 * El aviso se envía por el mismo canal que el resto de las notificaciones al
 * cliente (Web Push, fire-and-forget). Se marca {@code refundDelayNotified} tras
 * el intento para no reenviarlo más de una vez por pedido.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundDelayNotificationService {

    private final PaymentRepository paymentRepository;
    private final PushNotificationService pushNotificationService;

    /**
     * Notifica los reembolsos fallidos sin resolver hace más de {@code hours} horas.
     * Retorna la cantidad de pedidos avisados en esta corrida.
     */
    @Transactional
    public int notifyStaleRefundFailures(long hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        List<Payment> stale = paymentRepository.findStaleUnnotifiedRefundFailures(
                PaymentStatus.REFUND_FAILED, cutoff);

        for (Payment payment : stale) {
            Order order = payment.getOrder();
            String supportContact = order.getBranch().getPhone();

            // Push fire-and-forget: no conocemos el resultado de entrega de forma
            // síncrona. Marcamos notified tras el intento para garantizar "una sola
            // vez por pedido" (si el pedido no tiene suscripción push, no hay canal
            // alternativo — el cliente es anónimo —, así que igual se marca).
            pushNotificationService.sendRefundDelayNotice(order, supportContact);
            payment.setRefundDelayNotified(true);
            paymentRepository.save(payment);

            log.info("Refund delay notice sent | orderId={} mpPaymentId={} refundFailedAt={}",
                    order.getId(), payment.getMercadopagoPaymentId(), payment.getRefundFailedAt());
        }

        return stale.size();
    }
}
