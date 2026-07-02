package com.laroka.backend.media.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.laroka.backend.media.dto.MediaUploadResponseDTO;
import com.laroka.backend.media.service.MediaService;
import com.laroka.backend.shared.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint de subida de imágenes a Cloudflare R2 (US-15-01).
 *
 * Solo expone el endpoint; toda la lógica vive en {@link MediaService}. El
 * tenant se extrae del JWT del usuario autenticado, igual que en el resto de
 * endpoints de backoffice.
 */
@RestController
@RequestMapping("/backoffice/media")
@RequiredArgsConstructor
@Tag(name = "Backoffice Media", description = "Upload de imágenes a R2")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Subir imagen",
            description = "Sube una imagen (JPEG, PNG o WebP) a R2 y retorna su URL pública. "
                    + "No persiste ninguna entidad.")
    public ResponseEntity<MediaUploadResponseDTO> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String url = mediaService.upload(file, principal.getTenantId());
        return ResponseEntity.ok(new MediaUploadResponseDTO(url));
    }
}
