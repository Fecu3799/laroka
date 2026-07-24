package com.pedisur.backend.media.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de un objeto listado por el endpoint de media (US-R2-01): su URL
 * pública, el nombre original con el que se subió y la fecha de subida.
 *
 * {@code originalName} es {@code null} para objetos subidos antes de US-R2-01,
 * que no tienen la metadata custom.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaObjectResponseDTO {

    private String url;
    private String originalName;
    private Instant uploadedAt;
}
