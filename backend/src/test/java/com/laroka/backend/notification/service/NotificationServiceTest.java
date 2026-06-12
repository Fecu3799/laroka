package com.laroka.backend.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laroka.backend.order.dto.BackofficeOrderResponseDTO;
import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatus;

class NotificationServiceTest {

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService();
    }

    @Test
    void sendNewOrderEvent_emitsOnlyToCorrectBranch() throws Exception {
        SseEmitter emitterBranch1 = mock(SseEmitter.class);
        SseEmitter emitterBranch2 = mock(SseEmitter.class);

        service.register(1, emitterBranch1);
        service.register(2, emitterBranch2);

        service.sendNewOrderEvent(1, UUID.randomUUID(), LocalDateTime.now(), OrderOrigin.CLIENT);

        verify(emitterBranch1).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitterBranch2, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendNewOrderEvent_noSubscribers_doesNotThrow() {
        service.sendNewOrderEvent(99, UUID.randomUUID(), LocalDateTime.now(), OrderOrigin.CLIENT);
    }

    @Test
    void sendNewOrderEvent_deadEmitter_isRemovedAndDoesNotBlock() throws Exception {
        SseEmitter dead = mock(SseEmitter.class);
        SseEmitter alive = mock(SseEmitter.class);

        org.mockito.Mockito.doThrow(new java.io.IOException("broken pipe"))
                .when(dead).send(any(SseEmitter.SseEventBuilder.class));

        service.register(1, dead);
        service.register(1, alive);

        service.sendNewOrderEvent(1, UUID.randomUUID(), LocalDateTime.now(), OrderOrigin.CLIENT);

        verify(alive).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ── sendOrderUpdatedEvent ─────────────────────────────────────

    private BackofficeOrderResponseDTO orderDto(UUID id) {
        return BackofficeOrderResponseDTO.builder()
                .id(id)
                .status(OrderStatus.IN_PREPARATION)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void sendOrderUpdatedEvent_emitsOnlyToCorrectBranch() throws Exception {
        SseEmitter emitterBranch1 = mock(SseEmitter.class);
        SseEmitter emitterBranch2 = mock(SseEmitter.class);

        service.register(1, emitterBranch1);
        service.register(2, emitterBranch2);

        service.sendOrderUpdatedEvent(1, orderDto(UUID.randomUUID()), "BACKOFFICE");

        verify(emitterBranch1).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitterBranch2, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendOrderUpdatedEvent_noSubscribers_doesNotThrow() {
        service.sendOrderUpdatedEvent(99, orderDto(UUID.randomUUID()), "BACKOFFICE");
    }

    @Test
    void sendOrderUpdatedEvent_deadEmitter_isRemovedAndDoesNotBlock() throws Exception {
        SseEmitter dead = mock(SseEmitter.class);
        SseEmitter alive = mock(SseEmitter.class);

        org.mockito.Mockito.doThrow(new java.io.IOException("broken pipe"))
                .when(dead).send(any(SseEmitter.SseEventBuilder.class));

        service.register(1, dead);
        service.register(1, alive);

        service.sendOrderUpdatedEvent(1, orderDto(UUID.randomUUID()), "BACKOFFICE");

        verify(alive).send(any(SseEmitter.SseEventBuilder.class));
    }
}
