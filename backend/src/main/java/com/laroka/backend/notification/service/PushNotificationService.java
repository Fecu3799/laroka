package com.laroka.backend.notification.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Optional;

import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.laroka.backend.notification.entity.PushSubscription;
import com.laroka.backend.notification.repository.PushSubscriptionRepository;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

/**
 * Envío de notificaciones Web Push al cliente cuando su pedido cambia de estado
 * (US-09-03).
 *
 * Fire-and-forget: el envío corre en un hilo aparte (@Async) y nunca propaga
 * errores al caller, de modo que la entrega al Push Service no bloquea ni
 * revierte la transición de estado del pedido.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final String TITLE = "LaRoka";

    static {
        // web-push usa el provider "BC" para cargar la clave pública del cliente
        // y cifrar el payload, pero no lo registra por su cuenta.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushSubscriptionService pushSubscriptionService;
    private final PushService pushService;

    /**
     * Mensaje (body) por estado. Los estados ausentes no generan notificación.
     */
    private static String bodyFor(OrderStatus status) {
        return switch (status) {
            case IN_PREPARATION -> "Tu pedido está en preparación 🍕";
            case ON_THE_WAY -> "Tu pedido está en camino 🛵";
            case READY_FOR_PICKUP -> "Tu pedido está listo para retirar ✅";
            case DELIVERED -> "Tu pedido fue entregado ¡Buen provecho! 🎉";
            default -> null;
        };
    }

    /**
     * Envía la notificación push correspondiente a la nueva transición de estado.
     * Fire-and-forget: retorna void y nunca propaga excepciones al caller.
     */
    @Async
    public void sendNotification(Order order, OrderStatus newStatus) {
        if (order.getPushSubscriptionId() == null) {
            return;
        }

        String body = bodyFor(newStatus);
        if (body == null) {
            return;
        }

        Optional<PushSubscription> subscriptionOpt =
                pushSubscriptionRepository.findById(order.getPushSubscriptionId());
        if (subscriptionOpt.isEmpty()) {
            log.warn("Push notification skipped — subscription not found | orderId={} subscriptionId={}",
                    order.getId(), order.getPushSubscriptionId());
            return;
        }

        PushSubscription subscription = subscriptionOpt.get();
        String endpoint = subscription.getEndpoint();

        try {
            String payload = buildPayload(order, newStatus, body);
            Notification notification = buildNotification(subscription, payload.getBytes(StandardCharsets.UTF_8));

            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            handleStatusCode(statusCode, order, endpoint);
        } catch (Exception e) {
            // Cualquier fallo (red, criptografía, serialización…) se loguea y se
            // traga: nunca debe propagarse al flujo de transición de estado.
            log.warn("Push notification failed | orderId={} endpoint={}", order.getId(), endpoint, e);
        }
    }

    /**
     * Construye la Notification Web Push a partir de la suscripción. Aislado en
     * un método protegido para poder sustituirlo en tests sin depender de claves
     * EC reales (el constructor decodifica criptográficamente la clave pública).
     */
    protected Notification buildNotification(PushSubscription subscription, byte[] payload)
            throws GeneralSecurityException {
        return new Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                payload);
    }

    private String buildPayload(Order order, OrderStatus status, String body) {
        return "{"
                + "\"title\":\"" + escape(TITLE) + "\","
                + "\"body\":\"" + escape(body) + "\","
                + "\"orderId\":\"" + order.getId() + "\","
                + "\"status\":\"" + status.name() + "\""
                + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void handleStatusCode(int statusCode, Order order, String endpoint) {
        if (statusCode >= 200 && statusCode < 300) {
            log.debug("Push notification sent | orderId={} status={}", order.getId(), order.getStatus());
            return;
        }

        switch (statusCode) {
            case 404, 410 -> {
                // Suscripción expirada o desconocida: limpiar de la base.
                log.info("Push subscription expired ({}) — removing | orderId={} endpoint={}",
                        statusCode, order.getId(), endpoint);
                pushSubscriptionService.delete(endpoint);
            }
            case 401 -> log.error("Push notification VAPID auth problem (401) | orderId={} endpoint={}",
                    order.getId(), endpoint);
            case 429 -> log.warn("Push notification rate limited (429) | orderId={} endpoint={}",
                    order.getId(), endpoint);
            default -> log.warn("Push notification failed | status={} orderId={} endpoint={}",
                    statusCode, order.getId(), endpoint);
        }
    }
}
