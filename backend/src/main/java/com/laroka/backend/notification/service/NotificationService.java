package com.laroka.backend.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationService {

    private final CopyOnWriteArrayList<EmitterEntry> emitters = new CopyOnWriteArrayList<>();

    private record EmitterEntry(Integer branchId, SseEmitter emitter) {}

    public SseEmitter subscribe(Integer branchId) {
        SseEmitter emitter = new SseEmitter(0L);
        EmitterEntry entry = new EmitterEntry(branchId, emitter);
        emitters.add(entry);

        Runnable cleanup = () -> emitters.remove(entry);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> emitters.remove(entry));

        return emitter;
    }

    public void sendNewOrderEvent(Integer branchId, UUID orderId) {
        List<EmitterEntry> dead = new ArrayList<>();
        for (EmitterEntry entry : emitters) {
            if (entry.branchId().equals(branchId)) {
                try {
                    entry.emitter().send(SseEmitter.event()
                            .name("new-order")
                            .data(Map.of("orderId", orderId.toString())));
                } catch (Exception e) {
                    dead.add(entry);
                }
            }
        }
        emitters.removeAll(dead);
    }
}
