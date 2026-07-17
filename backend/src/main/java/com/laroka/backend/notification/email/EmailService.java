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
     * <p>Devuelve el resultado para que los callers que necesiten conocerlo puedan
     * reaccionar (ej. US-17-07 responde 502 si el envío falla). Los callers
     * best-effort (notificar reembolsos fallidos, etc.) simplemente ignoran el
     * valor de retorno.</p>
     *
     * @param to      destinatario
     * @param subject asunto
     * @param body    cuerpo del mensaje (texto plano)
     * @return {@code true} si el proveedor aceptó el envío; {@code false} si falló
     *         o si el servicio no está configurado. Nunca lanza excepción.
     */
    boolean send(String to, String subject, String body);
}
