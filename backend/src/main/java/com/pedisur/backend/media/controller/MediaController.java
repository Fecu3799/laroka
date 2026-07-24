package com.pedisur.backend.media.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pedisur.backend.media.dto.MediaObjectResponseDTO;
import com.pedisur.backend.media.dto.MediaUploadResponseDTO;
import com.pedisur.backend.media.service.MediaService;
import com.pedisur.backend.shared.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints de subida y listado de imágenes en Cloudflare R2 (US-15-01, US-R2-01).
 *
 * Solo exponen los endpoints; toda la lógica vive en {@link MediaService}. El
 * tenant se extrae del JWT del usuario autenticado, igual que en el resto de
 * endpoints de backoffice.
 */
@RestController
@RequestMapping("/backoffice/media")
@RequiredArgsConstructor
@Tag(name = "Backoffice Media", description = "Upload y listado de imágenes en R2")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // products/branches/logo son exclusivos de ADMIN/MANAGER. bug-reports (capturas
    // de reportes) lo puede subir cualquier rol operativo, incluido STAFF, porque el
    // reporte de bugs está disponible para todos los roles autenticados.
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or (#context == 'bug-reports' and hasRole('STAFF'))")
    @Operation(summary = "Subir imagen",
            description = "Sube una imagen (JPEG, PNG o WebP) a R2 bajo la subcarpeta del "
                    + "contexto (products, branches, logo o bug-reports) y retorna su URL pública. "
                    + "products/branches/logo requieren ADMIN o MANAGER; bug-reports lo puede subir "
                    + "también STAFF. No persiste ninguna entidad.")
    public ResponseEntity<MediaUploadResponseDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("context") String context,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String url = mediaService.upload(file, principal.getTenantId(), context);
        return ResponseEntity.ok(new MediaUploadResponseDTO(url));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Listar imágenes",
            description = "Lista las imágenes del tenant en la subcarpeta del contexto pedido "
                    + "(products, branches o logo), con su URL, nombre original y fecha de subida.")
    public ResponseEntity<List<MediaObjectResponseDTO>> list(
            @RequestParam("context") String context,
            @AuthenticationPrincipal CustomUserDetails principal) {
        List<MediaObjectResponseDTO> objects = mediaService.list(principal.getTenantId(), context)
                .stream()
                .map(object -> new MediaObjectResponseDTO(
                        object.url(), object.originalName(), object.uploadedAt()))
                .toList();
        return ResponseEntity.ok(objects);
    }
}
