package com.laroka.backend.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.service.OrderService;
import com.laroka.backend.payment.dto.WebhookEventDTO;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;
import com.laroka.backend.shared.exception.BusinessException;
import com.laroka.backend.shared.exception.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private PaymentGateway paymentGateway;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private PaymentService service;

    // --- helpers ---

    private WebhookEventDTO event(String type, String paymentId) {
        WebhookEventDTO event = new WebhookEventDTO();
        event.setType(type);
        event.setData(Map.of("id", paymentId));
        return event;
    }

    private Payment pendingPayment(UUID orderId) {
        Order order = Order.builder().id(orderId).build();
        return Payment.builder()
                .id(UUID.randomUUID())
                .status(PaymentStatus.PENDING)
                .method(PaymentMethod.MERCADOPAGO)
                .order(order)
                .build();
    }

    private Payment approvedPayment(String mpPaymentId) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .status(PaymentStatus.APPROVED)
                .method(PaymentMethod.MERCADOPAGO)
                .mercadopagoPaymentId(mpPaymentId)
                .order(Order.builder().id(UUID.randomUUID()).build())
                .build();
    }

    // --- initiatePayment ---

    @Test
    void initiatePayment_success_createsPaymentAndReturnsLink() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("3500.00"))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(orderId), any())).thenReturn(false);
        when(paymentGateway.createPreference(eq(orderId), any(), any()))
                .thenReturn("https://mp.com/pay?pref_id=pref123");
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String link = service.initiatePayment(orderId, null);

        assertThat(link).isEqualTo("https://mp.com/pay?pref_id=pref123");
        verify(paymentRepository).save(any(Payment.class));
    }

    // --- processWebhook: merchant_order ignored ---

    @Test
    void processWebhook_merchantOrderType_isIgnoredSilently() {
        service.processWebhook(null, null, null, event("merchant_order", "order-123"));

        verifyNoInteractions(paymentGateway, paymentRepository, orderService, notificationService);
    }

    // --- processWebhook: approved ---

    @Test
    void processWebhook_approved_activatesOrderAndNotifiesBackoffice() {
        UUID orderId = UUID.randomUUID();
        String paymentId = "mp-pay-001";

        when(paymentRepository.findByMercadopagoPaymentId(paymentId)).thenReturn(Optional.empty());
        when(paymentGateway.fetchPayment(paymentId))
                .thenReturn(new PaymentGateway.PaymentInfo("approved", orderId.toString()));

        Payment pending = pendingPayment(orderId);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Branch branch = Branch.builder().id(1).name("Playa Unión").build();
        Order order = Order.builder()
                .id(orderId).status(OrderStatus.PENDING_PAYMENT).branch(branch).build();
        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        service.processWebhook(null, null, paymentId, event("payment", paymentId));

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pending.getPaidAt()).isNotNull();
        verify(orderService).transitionStatus(orderId, OrderStatus.RECEIVED);
        verify(notificationService).sendNewOrderEvent(eq(1), eq(orderId), any());
    }

    // --- processWebhook: rejected ---

    @Test
    void processWebhook_rejected_orderRemainsInPendingPayment() {
        UUID orderId = UUID.randomUUID();
        String paymentId = "mp-pay-002";

        when(paymentRepository.findByMercadopagoPaymentId(paymentId)).thenReturn(Optional.empty());
        when(paymentGateway.fetchPayment(paymentId))
                .thenReturn(new PaymentGateway.PaymentInfo("rejected", orderId.toString()));

        Payment pending = pendingPayment(orderId);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processWebhook(null, null, paymentId, event("payment", paymentId));

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        verify(orderService, never()).transitionStatus(any(), any());
        verify(notificationService, never()).sendNewOrderEvent(any(), any(), any());
    }

    // --- processWebhook: duplicate ---

    @Test
    void processWebhook_duplicatePaymentId_skipsActivation() {
        String paymentId = "mp-pay-003";
        when(paymentRepository.findByMercadopagoPaymentId(paymentId))
                .thenReturn(Optional.of(approvedPayment(paymentId)));

        service.processWebhook(null, null, paymentId, event("payment", paymentId));

        verify(paymentGateway, never()).fetchPayment(any());
        verify(orderService, never()).transitionStatus(any(), any());
        verify(notificationService, never()).sendNewOrderEvent(any(), any(), any());
    }

    // --- processWebhook: order already activated (second guard) ---

    @Test
    void processWebhook_approvedButOrderAlreadyReceived_skipsActivationAndNotification() {
        UUID orderId = UUID.randomUUID();
        String paymentId = "mp-pay-006";

        // Payment has the mpPaymentId but is still PENDING (e.g. MP had previously returned "pending")
        // — so the early-return guard does NOT fire
        Payment pendingWithMpId = Payment.builder()
                .id(UUID.randomUUID())
                .status(PaymentStatus.PENDING)
                .method(PaymentMethod.MERCADOPAGO)
                .mercadopagoPaymentId(paymentId)
                .order(Order.builder().id(orderId).build())
                .build();
        when(paymentRepository.findByMercadopagoPaymentId(paymentId)).thenReturn(Optional.of(pendingWithMpId));
        when(paymentGateway.fetchPayment(paymentId))
                .thenReturn(new PaymentGateway.PaymentInfo("approved", orderId.toString()));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Order is already RECEIVED — second guard must prevent re-activation
        Branch branch = Branch.builder().id(1).name("Playa Unión").build();
        Order order = Order.builder()
                .id(orderId).status(OrderStatus.RECEIVED).branch(branch).build();
        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        service.processWebhook(null, "req-redelivery", paymentId, event("payment", paymentId));

        assertThat(pendingWithMpId.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(orderService, never()).transitionStatus(any(), any());
        verify(notificationService, never()).sendNewOrderEvent(any(), any(), any());
    }

    // --- processWebhook: invalid signature ---

    @Test
    void processWebhook_invalidSignature_throwsBusinessException() {
        ReflectionTestUtils.setField(service, "webhookSecret", "test-secret");

        assertThatThrownBy(() ->
                service.processWebhook("ts=12345,v1=badhash", "req-id-1", null, event("payment", "mp-pay-004")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválida");
    }

    // --- processWebhook: order/payment not found ---

    @Test
    void processWebhook_paymentNotFoundForOrder_throwsEntityNotFoundException() {
        UUID orderId = UUID.randomUUID();
        String paymentId = "mp-pay-005";

        when(paymentRepository.findByMercadopagoPaymentId(paymentId)).thenReturn(Optional.empty());
        when(paymentGateway.fetchPayment(paymentId))
                .thenReturn(new PaymentGateway.PaymentInfo("approved", orderId.toString()));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.processWebhook(null, null, paymentId, event("payment", paymentId)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // --- confirmCashPayment ---

    private Order orderWithBranch(UUID orderId, int branchId) {
        Branch branch = Branch.builder().id(branchId).name("Test").build();
        return Order.builder().id(orderId).status(OrderStatus.RECEIVED).branch(branch).build();
    }

    private Payment cashPayment(Order order, PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(status)
                .method(PaymentMethod.CASH)
                .build();
    }

    @Test
    void confirmCashPayment_pendingCash_approvesPayment() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithBranch(orderId, 1);
        Payment payment = cashPayment(order, PaymentStatus.PENDING);

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service.confirmCashPayment(orderId, 1);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.getPaidAt()).isNotNull();
    }

    @Test
    void confirmCashPayment_wrongBranch_throwsAccessDeniedException() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithBranch(orderId, 1);

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmCashPayment(orderId, 99))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void confirmCashPayment_alreadyApproved_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithBranch(orderId, 1);
        Payment payment = cashPayment(order, PaymentStatus.APPROVED);

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirmCashPayment(orderId, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("aprobado");
    }

    @Test
    void confirmCashPayment_notCashMethod_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithBranch(orderId, 1);
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(PaymentStatus.PENDING)
                .method(PaymentMethod.MERCADOPAGO)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirmCashPayment(orderId, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("efectivo");
    }
}
