package com.laroka.backend.notification.email;

/**
 * Señala que el proveedor de email no aceptó el envío en un flujo donde el fallo
 * NO es best-effort y el caller necesita reportarlo (ej. US-17-07: el operador
 * debe saber que su reporte de bug no llegó). El {@code GlobalExceptionHandler} la
 * mapea a HTTP 502 con un mensaje genérico, sin exponer detalles del proveedor.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }
}
