package com.pedisur.backend.order.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.pedisur.backend.order.entity.Order;

@Component
public class IdempotencyStore {

    private static final long TTL_MINUTES = 5;

    private record Entry(Order order, Instant timestamp) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public Optional<Order> get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.timestamp().isBefore(Instant.now().minus(TTL_MINUTES, ChronoUnit.MINUTES))) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.order());
    }

    public void put(String key, Order order) {
        store.put(key, new Entry(order, Instant.now()));
    }
}
