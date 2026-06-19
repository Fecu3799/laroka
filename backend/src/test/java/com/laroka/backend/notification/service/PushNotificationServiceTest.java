package com.laroka.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.notification.entity.PushSubscription;
import com.laroka.backend.notification.repository.PushSubscriptionRepository;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    // Par p256dh/auth de ejemplo válido (punto EC P-256 sin comprimir) usado
    // habitualmente en suites de prueba de Web Push. Debe ser válido porque el
    // constructor de Notification decodifica la clave antes de enviar.
    private static final String P256DH =
            "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
    private static final String AUTH = "tBHItJI5svbpez7KI4CCXg";
    private static final String ENDPOINT = "https://push.example.com/sub/abc123";

    @Mock private PushSubscriptionRepository pushSubscriptionRepository;
    @Mock private PushSubscriptionService pushSubscriptionService;
    @Mock private PushService pushService;

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(
                pushSubscriptionRepository, pushSubscriptionService, pushService);
    }

    // --- helpers ---

    private Order orderWithSubscription(UUID subscriptionId) {
        return Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.RECEIVED)
                .pushSubscriptionId(subscriptionId)
                .build();
    }

    private PushSubscription subscription(UUID id) {
        return PushSubscription.builder()
                .id(id)
                .endpoint(ENDPOINT)
                .p256dh(P256DH)
                .auth(AUTH)
                .build();
    }

    private void stubSend(int statusCode) throws Exception {
        HttpResponse response = org.mockito.Mockito.mock(HttpResponse.class);
        StatusLine statusLine = org.mockito.Mockito.mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(statusCode);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any(Notification.class))).thenReturn(response);
    }

    // --- tests ---

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class,
            names = {"IN_PREPARATION", "ON_THE_WAY", "READY_FOR_PICKUP", "DELIVERED"})
    void validTransition_sendsNotification(OrderStatus status) throws Exception {
        UUID subId = UUID.randomUUID();
        Order order = orderWithSubscription(subId);
        when(pushSubscriptionRepository.findById(subId)).thenReturn(Optional.of(subscription(subId)));
        stubSend(201);

        service.sendNotification(order, status);

        verify(pushService, times(1)).send(any(Notification.class));
        verify(pushSubscriptionService, never()).delete(any());
    }

    @Test
    void statusWithoutMessage_doesNotSend() throws Exception {
        Order order = orderWithSubscription(UUID.randomUUID());

        service.sendNotification(order, OrderStatus.RECEIVED);

        verify(pushSubscriptionRepository, never()).findById(any());
        verify(pushService, never()).send(any(Notification.class));
    }

    @Test
    void orderWithoutSubscription_doesNotSend() throws Exception {
        Order order = orderWithSubscription(null);

        service.sendNotification(order, OrderStatus.IN_PREPARATION);

        verify(pushSubscriptionRepository, never()).findById(any());
        verify(pushService, never()).send(any(Notification.class));
    }

    @Test
    void gone410_deletesSubscriptionAndUnlinksOrder() throws Exception {
        UUID subId = UUID.randomUUID();
        Order order = orderWithSubscription(subId);
        when(pushSubscriptionRepository.findById(subId)).thenReturn(Optional.of(subscription(subId)));
        stubSend(410);

        service.sendNotification(order, OrderStatus.ON_THE_WAY);

        verify(pushSubscriptionService, times(1)).delete(eq(ENDPOINT));
    }

    @Test
    void unauthorized401_logsErrorButDoesNotDelete() throws Exception {
        UUID subId = UUID.randomUUID();
        Order order = orderWithSubscription(subId);
        when(pushSubscriptionRepository.findById(subId)).thenReturn(Optional.of(subscription(subId)));
        stubSend(401);

        service.sendNotification(order, OrderStatus.READY_FOR_PICKUP);

        verify(pushService, times(1)).send(any(Notification.class));
        verify(pushSubscriptionService, never()).delete(any());
    }

    @Test
    void genericException_isNotPropagated() throws Exception {
        UUID subId = UUID.randomUUID();
        Order order = orderWithSubscription(subId);
        when(pushSubscriptionRepository.findById(subId)).thenReturn(Optional.of(subscription(subId)));
        when(pushService.send(any(Notification.class))).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> service.sendNotification(order, OrderStatus.DELIVERED))
                .doesNotThrowAnyException();
        verify(pushSubscriptionService, never()).delete(any());
    }
}
