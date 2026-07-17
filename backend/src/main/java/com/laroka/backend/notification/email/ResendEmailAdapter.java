package com.laroka.backend.notification.email;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de {@link EmailService} sobre la API HTTP de Resend
 * (https://resend.com). Sigue el mismo criterio que el adapter de MercadoPago:
 * cliente HTTP nativo ({@link RestClient}) en vez de un SDK pesado.
 */
@Slf4j
@Component
public class ResendEmailAdapter implements EmailService {

    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String from;
    private final RestClient restClient;

    @Autowired
    public ResendEmailAdapter(
            @Value("${email.api-key:}") String apiKey,
            @Value("${email.from:}") String from) {
        this(apiKey, from, RestClient.builder());
    }

    // Constructor de test: permite inyectar un RestClient.Builder ligado a un
    // MockRestServiceServer para verificar el body de la request sin HTTP real.
    ResendEmailAdapter(String apiKey, String from, RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        this.from = from;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean send(String to, String subject, String body) {
        log.info("send: to={}, subject={}", to, subject);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("send: email api-key not configured, skipping send — to={}, subject={}", to, subject);
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(to));
        payload.put("subject", subject);
        payload.put("text", body);

        try {
            restClient.post()
                    .uri(RESEND_EMAILS_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("send: email accepted by provider — to={}, subject={}", to, subject);
            return true;
        } catch (Exception e) {
            // Best-effort (US-17-06): el fallo del proveedor se loguea y NO se propaga,
            // para no romper el flujo que dispara el envío (igual criterio que Web Push
            // y los reembolsos automáticos). El caller decide qué hacer con el false.
            log.error("send: error sending email via provider — to={}, subject={}, error={}",
                    to, subject, e.getMessage());
            return false;
        }
    }
}
