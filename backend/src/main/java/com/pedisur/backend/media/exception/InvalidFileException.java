package com.pedisur.backend.media.exception;

/**
 * El archivo recibido no es válido para subir (US-15-01): tipo no permitido,
 * tamaño excedido o contenido vacío/ilegible. Se mapea a HTTP 400.
 */
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }
}
