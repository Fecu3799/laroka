package com.pedisur.backend.media.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del endpoint de upload de imágenes (US-15-01): la URL pública del
 * archivo subido a R2.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaUploadResponseDTO {

    private String url;
}
