package com.pedisur.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pedisur.backend.notification.entity.PushSubscription;
import com.pedisur.backend.notification.repository.PushSubscriptionRepository;
import com.pedisur.backend.order.entity.Order;
import com.pedisur.backend.order.entity.OrderStatus;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    // Placeholders ficticios: la construcción real de la Notification (que sí
    // decodifica criptográficamente la clave EC) se sustituye con un spy sobre
    // buildNotification, así que estos valores nunca se decodifican.
    private static final String P256DH = "test-public-key";
    private static final String AUTH = "test-auth-key";
    private static final String ENDPOINT = "https://push.example.com/sub/abc123";

    @Mock private PushSubscriptionRepository pushSubscriptionRepository;
    @Mock private PushSubscriptionService pushSubscriptionService;
    @Mock private PushService pushService;

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = spy(new PushNotificationService(
                pushSubscriptionRepository, pushSubscriptionService, pushService));
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

    /** Evita la construcción real (criptográfica) de la Notification. */
    private void stubNotificationBuild() throws Exception {
        doReturn(mock(Notification.class)).when(service).buildNotification(any(), any());
    }

    private void stubSend(int statusCode) throws Exception {
        stubNotificationBuild();
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
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
    void cancelledTransition_sendsNeutralRefundMessage() throws Exception {
        UUID subId = UUID.randomUUID();
        Order order = orderWithSubscription(subId);
        when(pushSubscriptionRepository.findById(subId)).thenReturn(Optional.of(subscription(subId)));
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doReturn(mock(Notification.class)).when(service).buildNotification(any(), payloadCaptor.capture());
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(201);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any(Notification.class))).thenReturn(response);

        service.sendNotification(order, OrderStatus.CANCELLED);

        verify(pushService, times(1)).send(any(Notification.class));
        String payload = new String(payloadCaptor.getValue(), StandardCharsets.UTF_8);
        // Neutro: menciona el reembolso pero no el resultado interno (éxito/fallo).
        assertThat(payload).contains("reembolso").contains("cancelado");
        assertThat(payload.toLowerCase()).doesNotContain("error").doesNotContain("falló");
    }

    @Test
    void sendRefundDelayNotice_sendsPushWithSupportContact() throws Exception {
        UUID subId = UUID.randomUUID();
        Order order = orderWithSubscription(subId);
        when(pushSubscriptionRepository.findById(subId)).thenReturn(Optional.of(subscription(subId)));
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        doReturn(mock(Notification.class)).when(service).buildNotification(any(), payloadCaptor.capture());
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(201);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any(Notification.class))).thenReturn(response);

        service.sendRefundDelayNotice(order, "+542804555000");

        verify(pushService, times(1)).send(any(Notification.class));
        String payload = new String(payloadCaptor.getValue(), StandardCharsets.UTF_8);
        assertThat(payload).contains("+542804555000").contains("reembolso");
    }

    @Test
    void sendRefundDelayNotice_noSubscription_doesNotSend() throws Exception {
        Order order = orderWithSubscription(null);

        service.sendRefundDelayNotice(order, "+542804555000");

        verify(pushSubscriptionRepository, never()).findById(any());
        verify(pushService, never()).send(any(Notification.class));
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
        stubNotificationBuild();
        when(pushService.send(any(Notification.class))).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> service.sendNotification(order, OrderStatus.DELIVERED))
                .doesNotThrowAnyException();
        verify(pushSubscriptionService, never()).delete(any());
    }
}
