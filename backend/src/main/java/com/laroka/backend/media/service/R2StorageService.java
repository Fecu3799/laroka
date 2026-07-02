package com.laroka.backend.media.service;

import org.springframework.stereotype.Service;

import com.laroka.backend.media.config.R2Config;
import com.laroka.backend.media.exception.StorageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Adapter de almacenamiento sobre Cloudflare R2 (US-15-01).
 *
 * Implementa {@link StorageService} usando el cliente S3 v2 configurado en
 * {@link R2Config}. Traduce cualquier fallo del SDK a {@link StorageException}
 * sin propagar detalles del proveedor hacia arriba.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService implements StorageService {

    private final S3Client r2S3Client;
    private final R2Config r2Config;

    @Override
    public String upload(byte[] content, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        try {
            r2S3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (Exception ex) {
            // No exponer detalles del proveedor: se loguea internamente y se
            // propaga una excepción genérica que el handler mapea a 502.
            log.error("Fallo al subir objeto a R2 (key={}): {}", key, ex.getMessage(), ex);
            throw new StorageException("No se pudo subir el archivo al almacenamiento", ex);
        }

        return buildPublicUrl(key);
    }

    private String buildPublicUrl(String key) {
        String base = r2Config.getPublicUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + key;
    }
}
