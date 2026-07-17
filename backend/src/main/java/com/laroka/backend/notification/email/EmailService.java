package com.laroka.backend.notification.email;

/**
 * Servicio genérico de envío de email transaccional (US-17-06). No está acoplado a
 * ningún caso de uso concreto (bug reports, notificaciones al ADMIN de reembolsos
 * fallidos, etc.) para poder reutilizarse.
 *
 * <p>La implementación es <b>best-effort</b>: un fallo del proveedor se loguea y
 * <b>no</b> se propaga al caller, para no romper el flujo que dispara el envío
 * (mismo criterio que Web Push y los reembolsos).</p>
 */
public interface EmailService {

    /**
     * Envía un email. Best-effort: nunca lanza excepción por fallo del proveedor.
     *
     * @param to      destinatario
     * @param subject asunto
     * @param body    cuerpo del mensaje (texto plano)
     */
    void send(String to, String subject, String body);
}
