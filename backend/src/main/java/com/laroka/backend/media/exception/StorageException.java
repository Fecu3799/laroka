package com.laroka.backend.media.exception;

/**
 * Fallo al operar contra el almacenamiento externo (R2) en US-15-01. Se mapea a
 * HTTP 502 con un mensaje genérico, sin exponer detalles del proveedor.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
