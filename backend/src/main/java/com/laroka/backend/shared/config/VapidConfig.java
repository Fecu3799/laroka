package com.laroka.backend.shared.config;

import java.security.GeneralSecurityException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import nl.martijndwars.webpush.PushService;

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

    @Value("${vapid.subject:mailto:soporte@laroka.app}")
    private String subject;

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }

    /**
     * Cliente Web Push Protocol (nl.martijndwars:web-push) usado por
     * PushNotificationService para entregar notificaciones (US-09-03).
     *
     * Se construye siempre como singleton. Las claves VAPID solo se cargan si
     * están configuradas; en entornos sin push (test/dev) se devuelve un cliente
     * sin claves cuyos envíos fallarán de forma controlada y serán logueados por
     * el servicio, sin romper el arranque del contexto.
     */
    @Bean
    public PushService pushService() throws GeneralSecurityException {
        PushService pushService = new PushService();
        if (isConfigured()) {
            pushService.setPublicKey(publicKey);
            pushService.setPrivateKey(privateKey);
        }
        pushService.setSubject(subject);
        return pushService;
    }
}
