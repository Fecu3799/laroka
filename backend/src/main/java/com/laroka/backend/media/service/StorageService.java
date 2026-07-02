package com.laroka.backend.media.service;

import java.util.List;

/**
 * Puerto de almacenamiento de objetos (US-15-01, extendido en US-R2-01).
 *
 * Abstrae el proveedor concreto (hoy Cloudflare R2 vía {@link R2StorageService})
 * detrás de una interfaz, según la regla de arquitectura "integraciones externas
 * siempre via interfaz + Adapter".
 */
public interface StorageService {

    /**
     * Sube un objeto al bucket y retorna su URL pública.
     *
     * @param content      bytes del objeto
     * @param key          ruta/clave dentro del bucket (ej: {tenantId}/{context}/{uuid}.jpg)
     * @param contentType  Content-Type del objeto
     * @param originalName nombre original del archivo, guardado como metadata
     *                     custom; si es {@code null}/vacío no se persiste metadata
     * @return URL pública del objeto subido
     * @throws com.laroka.backend.media.exception.StorageException si el upload falla
     */
    String upload(byte[] content, String key, String contentType, String originalName);

    /**
     * Lista los objetos cuya clave empieza por {@code prefix} (una subcarpeta del
     * bucket), incluyendo su nombre original y fecha de subida.
     *
     * @param prefix prefijo/subcarpeta a listar (ej: {tenantId}/{context}/)
     * @return objetos encontrados; lista vacía si no hay ninguno
     * @throws com.laroka.backend.media.exception.StorageException si el listado falla
     */
    List<StoredObject> list(String prefix);
}
