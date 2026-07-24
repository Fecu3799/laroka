package com.pedisur.backend.notification.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedisur.backend.notification.entity.PushSubscription;
import com.pedisur.backend.notification.repository.PushSubscriptionRepository;
import com.pedisur.backend.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final OrderRepository orderRepository;

    /**
     * Inserta o actualiza una suscripción Web Push identificada por su endpoint.
     * Si el endpoint ya existe, refresca las claves p256dh y auth; si no, crea
     * una nueva suscripción. (US-09-01)
     *
     * @return el id de la suscripción persistida.
     */
    @Transactional
    public UUID upsert(String endpoint, String p256dh, String auth) {
        Optional<PushSubscription> existing = pushSubscriptionRepository.findByEndpoint(endpoint);

        if (existing.isPresent()) {
            PushSubscription subscription = existing.get();
            subscription.setP256dh(p256dh);
            subscription.setAuth(auth);
            log.debug("Push subscription updated | id={}", subscription.getId());
            return subscription.getId();
        }

        PushSubscription created = pushSubscriptionRepository.save(PushSubscription.builder()
                .endpoint(endpoint)
                .p256dh(p256dh)
                .auth(auth)
                .build());
        log.debug("Push subscription created | id={}", created.getId());
        return created.getId();
    }

    /**
     * Elimina la suscripción asociada al endpoint y desvincula las órdenes que la
     * referencian (push_subscription_id = NULL). Idempotente: si el endpoint no
     * existe no hace nada. (US-09-01)
     */
    @Transactional
    public void delete(String endpoint) {
        Optional<PushSubscription> existing = pushSubscriptionRepository.findByEndpoint(endpoint);
        if (existing.isEmpty()) {
            log.debug("Push subscription delete | endpoint not found, no-op");
            return;
        }

        PushSubscription subscription = existing.get();
        int cleared = orderRepository.clearPushSubscription(subscription.getId());
        pushSubscriptionRepository.delete(subscription);
        log.debug("Push subscription deleted | id={} ordersUnlinked={}", subscription.getId(), cleared);
    }
}
