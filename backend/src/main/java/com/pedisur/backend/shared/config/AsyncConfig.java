package com.pedisur.backend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita el procesamiento asíncrono (@Async) en el contexto de Spring.
 *
 * Necesario para el envío fire-and-forget de notificaciones push, de modo que
 * la entrega al Push Service no bloquee ni revierta la transición de estado del
 * pedido (US-09-03).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
