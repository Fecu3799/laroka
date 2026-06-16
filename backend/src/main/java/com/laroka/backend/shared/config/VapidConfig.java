package com.laroka.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

/**
 * Claves VAPID para el Web Push Protocol (US-09-01).
 *
 * Se inyectan desde variables de entorno. Los defaults vacíos evitan que el
 * contexto falle al arrancar cuando las claves no están definidas (perfil test
 * y entornos donde el push aún no se configuró). El envío real de notificaciones
 * debe validar que ambas claves estén presentes antes de operar.
 */
@Getter
@Configuration
public class VapidConfig {

    @Value("${vapid.public-key:}")
    private String publicKey;

    @Value("${vapid.private-key:}")
    private String privateKey;

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
