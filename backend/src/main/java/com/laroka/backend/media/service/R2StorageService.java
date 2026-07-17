package com.laroka.backend.media.service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.laroka.backend.media.config.R2Config;
import com.laroka.backend.media.exception.StorageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Adapter de almacenamiento sobre Cloudflare R2 (US-15-01, extendido en US-R2-01).
 *
 * Implementa {@link StorageService} usando el cliente S3 v2 configurado en
 * {@link R2Config}. Traduce cualquier fallo del SDK a {@link StorageException}
 * sin propagar detalles del proveedor hacia arriba.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService implements StorageService {

    /**
     * Clave de metadata custom donde se guarda el nombre original del archivo. S3
     * la almacena como cabecera {@code x-amz-meta-original-name} y la devuelve en
     * minúsculas y sin el prefijo al leerla.
     */
    private static final String METADATA_ORIGINAL_NAME = "original-name";

    private final S3Client r2S3Client;
    private final R2Config r2Config;

    @Override
    public String upload(byte[] content, String key, String contentType, String originalName) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length);

        if (originalName != null && !originalName.isBlank()) {
            // R2/S3 firma las cabeceras de metadata; un valor con caracteres
            // no-ASCII (acentos, ñ, emojis…) rompe el cálculo de firma y el SDK
            // responde SignatureDoesNotMatch. Se URL-encodea para garantizar un
            // valor ASCII; readOriginalName lo decodifica al leerlo de vuelta.
            String encodedName = URLEncoder.encode(originalName, StandardCharsets.UTF_8);
            builder.metadata(Map.of(METADATA_ORIGINAL_NAME, encodedName));
        }

        try {
            r2S3Client.putObject(builder.build(), RequestBody.fromBytes(content));
        } catch (Exception ex) {
            // No exponer detalles del proveedor: se loguea internamente y se
            // propaga un mensaje claro (fallo de storage) que el handler mapea a 502.
            log.error("Fallo al subir objeto a R2 (key={}): {}", key, ex.getMessage(), ex);
            throw new StorageException(
                    "No se pudo guardar el archivo en el almacenamiento. Intentá de nuevo en unos minutos.", ex);
        }

        return buildPublicUrl(key);
    }

    @Override
    public List<StoredObject> list(String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(r2Config.getBucketName())
                .prefix(prefix)
                .build();

        try {
            ListObjectsV2Response response = r2S3Client.listObjectsV2(request);
            List<StoredObject> objects = new ArrayList<>();
            for (S3Object object : response.contents()) {
                objects.add(new StoredObject(
                        buildPublicUrl(object.key()),
                        readOriginalName(object.key()),
                        object.lastModified()));
            }
            return objects;
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Fallo al listar objetos de R2 (prefix={}): {}", prefix, ex.getMessage(), ex);
            throw new StorageException("No se pudo listar el almacenamiento", ex);
        }
    }

    /**
     * Lee el nombre original desde la metadata custom del objeto. Devuelve
     * {@code null} si el objeto no tiene esa metadata (subido antes de US-R2-01),
     * sin romper el listado.
     */
    private String readOriginalName(String key) {
        HeadObjectResponse head = r2S3Client.headObject(HeadObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(key)
                .build());
        String raw = head.metadata().get(METADATA_ORIGINAL_NAME);
        // El nombre se persiste URL-encodeado (ver upload) para soportar caracteres
        // no-ASCII; se decodifica para devolver el nombre original tal cual.
        return raw == null ? null : URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private String buildPublicUrl(String key) {
        String base = r2Config.getPublicUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + key;
    }
}
