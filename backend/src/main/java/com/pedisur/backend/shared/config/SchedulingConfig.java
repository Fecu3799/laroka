package com.pedisur.backend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita los jobs @Scheduled (expiración de pedidos, aviso de demora de reembolso,
 * auto-cierre de turno) en todo perfil que no sea de test.
 *
 * Se excluye el perfil {@code test} a propósito: los jobs usan
 * {@code @Scheduled(fixedDelay)} sin initialDelay, así que disparan apenas arranca el
 * contexto Spring. En un @SpringBootTest eso los hacía correr en el hilo del scheduler
 * contra orders/payment/work_shift justo cuando el @BeforeEach de un test de
 * integración ejecuta {@code TRUNCATE ... CASCADE}, provocando deadlocks intermitentes.
 * Los tests que ejercitan un job lo invocan directamente (el service o {@code job.run()}),
 * nunca vía el scheduler, así que desactivarlo acá no reduce cobertura.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
