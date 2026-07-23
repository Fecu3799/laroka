package com.laroka.backend.payment.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.laroka.backend.payment.service.RefundDelayNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job periódico (US-17-08): avisa al cliente cuando su reembolso lleva más de
 * N horas sin resolverse (Payment en REFUND_FAILED). Mismo patrón que
 * {@code ShiftAutoCloseJob} / {@code OrderExpirationJob}: chequeo cada 15 min,
 * lógica delegada al service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundDelayNotificationJob {

    private final RefundDelayNotificationService refundDelayNotificationService;

    @Value("${refund.delay-notice-hours:24}")
    private long delayNoticeHours;

    @Scheduled(fixedDelay = 900_000)
    public void run() {
        int notified = refundDelayNotificationService.notifyStaleRefundFailures(delayNoticeHours);
        if (notified > 0) {
            log.info("RefundDelayNotificationJob: {} aviso(s) de demora de reembolso enviado(s)", notified);
        }
    }
}
