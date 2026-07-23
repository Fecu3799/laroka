package com.laroka.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.notification.service.PushNotificationService;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class RefundDelayNotificationServiceTest {

    private static final String SUPPORT_PHONE = "+542804555000";

    @Mock private PaymentRepository paymentRepository;
    @Mock private PushNotificationService pushNotificationService;
    @InjectMocks private RefundDelayNotificationService service;

    private Payment staleRefundFailure() {
        Branch branch = Branch.builder().id(1).name("Centro").phone(SUPPORT_PHONE).build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .branch(branch)
                .pushSubscriptionId(UUID.randomUUID())
                .build();
        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(PaymentStatus.REFUND_FAILED)
                .mercadopagoPaymentId("mp-stale-1")
                .refundFailedAt(LocalDateTime.now().minusHours(30))
                .build();
    }

    @Test
    void notifiesStaleFailure_sendsNoticeAndMarksNotifiedOnce() {
        Payment payment = staleRefundFailure();
        when(paymentRepository.findStaleUnnotifiedRefundFailures(eq(PaymentStatus.REFUND_FAILED), any()))
                .thenReturn(List.of(payment));

        int count = service.notifyStaleRefundFailures(24);

        assertThat(count).isEqualTo(1);
        // Aviso por el canal push con el contacto de soporte de la sucursal.
        verify(pushNotificationService).sendRefundDelayNotice(payment.getOrder(), SUPPORT_PHONE);
        // Marcado como notificado y persistido → no se reenvía en corridas futuras.
        assertThat(payment.isRefundDelayNotified()).isTrue();
        verify(paymentRepository).save(payment);
    }

    @Test
    void computesCutoffFromConfiguredHours() {
        when(paymentRepository.findStaleUnnotifiedRefundFailures(eq(PaymentStatus.REFUND_FAILED), any()))
                .thenReturn(List.of());
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        service.notifyStaleRefundFailures(6);

        verify(paymentRepository).findStaleUnnotifiedRefundFailures(eq(PaymentStatus.REFUND_FAILED),
                cutoffCaptor.capture());
        LocalDateTime cutoff = cutoffCaptor.getValue();
        // cutoff ≈ now - 6h (con holgura para el tiempo de ejecución del test).
        assertThat(cutoff).isBefore(LocalDateTime.now().minusHours(5))
                .isAfter(LocalDateTime.now().minusHours(7));
    }

    @Test
    void noStaleFailures_sendsNothing() {
        when(paymentRepository.findStaleUnnotifiedRefundFailures(eq(PaymentStatus.REFUND_FAILED), any()))
                .thenReturn(List.of());

        int count = service.notifyStaleRefundFailures(24);

        assertThat(count).isZero();
        verify(pushNotificationService, never()).sendRefundDelayNotice(any(), any());
        verify(paymentRepository, never()).save(any());
    }
}
