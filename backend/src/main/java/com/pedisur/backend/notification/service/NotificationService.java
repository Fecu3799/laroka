package com.pedisur.backend.notification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.pedisur.backend.order.dto.BackofficeOrderResponseDTO;
import com.pedisur.backend.order.entity.OrderOrigin;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {

    private static final long SSE_TIMEOUT_MS = 3 * 60 * 1000L;

    private final CopyOnWriteArrayList<EmitterEntry> emitters = new CopyOnWriteArrayList<>();

    private record EmitterEntry(Integer branchId, SseEmitter emitter) {}

    public SseEmitter subscribe(Integer branchId) {
        return register(branchId, new SseEmitter(SSE_TIMEOUT_MS));
    }

    SseEmitter register(Integer branchId, SseEmitter emitter) {
        EmitterEntry entry = new EmitterEntry(branchId, emitter);
        emitters.add(entry);

        Runnable cleanup = () -> emitters.remove(entry);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> emitters.remove(entry));

        log.debug("SSE subscriber registered | branchId={}", branchId);
        return emitter;
    }

    public void sendNewOrderEvent(Integer branchId, UUID orderId, LocalDateTime createdAt, OrderOrigin origin) {
        List<EmitterEntry> dead = new ArrayList<>();
        for (EmitterEntry entry : emitters) {
            if (entry.branchId().equals(branchId)) {
                try {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "NEW_ORDER");
                    data.put("orderId", orderId.toString());
                    data.put("branchId", branchId);
                    data.put("createdAt", createdAt.toString());
                    data.put("origin", origin != null ? origin.name() : OrderOrigin.CLIENT.name());
                    entry.emitter().send(SseEmitter.event().name("new-order").data(data));
                } catch (Exception e) {
                    log.warn("SSE send failed, removing dead emitter | branchId={}", branchId);
                    dead.add(entry);
                }
            }
        }
        emitters.removeAll(dead);
    }

    public void sendOrderUpdatedEvent(Integer branchId, BackofficeOrderResponseDTO orderDto, String actionOrigin) {
        log.debug("SSE order-updated | branchId={} orderId={} status={} actionOrigin={}", branchId, orderDto.getId(), orderDto.getStatus(), actionOrigin);
        List<EmitterEntry> dead = new ArrayList<>();
        for (EmitterEntry entry : emitters) {
            if (entry.branchId().equals(branchId)) {
                try {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "ORDER_UPDATED");
                    data.put("branchId", branchId);
                    data.put("actionOrigin", actionOrigin);
                    data.put("order", orderDto);
                    entry.emitter().send(SseEmitter.event().name("order-updated").data(data));
                } catch (Exception e) {
                    log.warn("SSE send failed, removing dead emitter | branchId={}", branchId);
                    dead.add(entry);
                }
            }
        }
        emitters.removeAll(dead);
    }

    public void sendCancellationRequestEvent(Integer branchId, UUID orderId) {
        log.info("SSE cancellation-request | branchId={} orderId={} activeEmitters={}", branchId, orderId, emitters.size());
        List<EmitterEntry> dead = new ArrayList<>();
        for (EmitterEntry entry : emitters) {
            if (entry.branchId().equals(branchId)) {
                try {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "CANCELLATION_REQUESTED");
                    data.put("orderId", orderId.toString());
                    data.put("branchId", branchId);
                    entry.emitter().send(SseEmitter.event().name("cancellation-request").data(data));
                    log.info("SSE cancellation-request sent | branchId={} orderId={}", branchId, orderId);
                } catch (Exception e) {
                    log.warn("SSE send failed, removing dead emitter | branchId={}", branchId);
                    dead.add(entry);
                }
            }
        }
        emitters.removeAll(dead);
    }

    public void sendShiftAutoClosedEvent(Integer branchId) {
        List<EmitterEntry> dead = new ArrayList<>();
        for (EmitterEntry entry : emitters) {
            if (entry.branchId().equals(branchId)) {
                try {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", "SHIFT_AUTO_CLOSED");
                    data.put("branchId", branchId);
                    entry.emitter().send(SseEmitter.event().name("shift-auto-closed").data(data));
                } catch (Exception e) {
                    log.warn("SSE send failed, removing dead emitter | branchId={}", branchId);
                    dead.add(entry);
                }
            }
        }
        emitters.removeAll(dead);
    }
}
