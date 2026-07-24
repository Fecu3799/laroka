package com.pedisur.backend.media.service;

import java.time.Instant;

/**
 * Objeto almacenado en R2, tal como lo devuelve el listado (US-R2-01).
 *
 * Es el modelo que cruza la frontera del {@link StorageService} hacia el
 * {@link MediaService}: no es un DTO de API. El controller lo convierte a
 * {@code MediaObjectResponseDTO} antes de retornarlo.
 *
 * @param url          URL pública del objeto
 * @param originalName nombre original del archivo (metadata custom); {@code null}
 *                     si el objeto no la tiene (subido antes de US-R2-01)
 * @param uploadedAt   fecha de subida (última modificación del objeto en R2)
 */
public record StoredObject(String url, String originalName, Instant uploadedAt) {
}
