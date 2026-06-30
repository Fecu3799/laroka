package com.laroka.backend.media.service;

/**
 * Puerto de almacenamiento de objetos (US-15-01).
 *
 * Abstrae el proveedor concreto (hoy Cloudflare R2 vía {@link R2StorageService})
 * detrás de una interfaz, según la regla de arquitectura "integraciones externas
 * siempre via interfaz + Adapter".
 */
public interface StorageService {

    /**
     * Sube un objeto al bucket y retorna su URL pública.
     *
     * @param content     bytes del objeto
     * @param key         ruta/clave dentro del bucket (ej: {tenantId}/{uuid}.jpg)
     * @param contentType Content-Type del objeto
     * @return URL pública del objeto subido
     * @throws com.laroka.backend.media.exception.StorageException si el upload falla
     */
    String upload(byte[] content, String key, String contentType);
}
