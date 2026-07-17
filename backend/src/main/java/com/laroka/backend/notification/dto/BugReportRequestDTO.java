package com.laroka.backend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Reporte de bug enviado desde el backoffice (US-17-07). {@code url} y
 * {@code userAgent} los captura automáticamente el frontend (window.location /
 * navigator.userAgent), no los tipea el operador.
 */
@Data
public class BugReportRequestDTO {

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    private String url;

    private String userAgent;

    /** Opcional: URL pública de una captura de pantalla subida a R2 (contexto bug-reports). */
    private String screenshotUrl;
}
