package com.laroka.backend.media.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.laroka.backend.media.exception.InvalidFileException;
import com.laroka.backend.media.exception.StorageException;

import lombok.RequiredArgsConstructor;

/**
 * Lógica de negocio del upload de imágenes (US-15-01).
 *
 * Valida tipo y tamaño del archivo, organiza la clave por tenant
 * ({tenantId}/{uuid}.{ext}) y delega la subida al {@link StorageService}. No
 * persiste nada en base de datos: solo sube y retorna la URL pública.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CONTENT_TYPE_WEBP = "image/webp";

    private final StorageService storageService;

    @Value("${media.max-upload-size-mb:5}")
    private long maxUploadSizeMb;

    /**
     * Sube la imagen recibida al almacenamiento y retorna su URL pública.
     *
     * @param file     archivo multipart recibido (campo "file")
     * @param tenantId tenant del usuario autenticado (extraído del JWT)
     * @return URL pública del archivo subido
     * @throws InvalidFileException si el tipo no es permitido, el tamaño excede
     *                              el máximo o el archivo está vacío
     * @throws StorageException     si el almacenamiento falla
     */
    public String upload(MultipartFile file, Integer tenantId) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("El archivo es obligatorio y no puede estar vacío");
        }

        String extension = resolveExtension(file.getContentType());

        long maxBytes = maxUploadSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new InvalidFileException(
                    "El archivo excede el tamaño máximo permitido de " + maxUploadSizeMb + " MB");
        }

        String key = tenantId + "/" + UUID.randomUUID() + extension;

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new StorageException("No se pudo leer el archivo recibido", ex);
        }

        return storageService.upload(content, key, file.getContentType());
    }

    /**
     * Deriva la extensión del archivo a partir del Content-Type. Solo se aceptan
     * JPEG, PNG y WebP; cualquier otro tipo es rechazado con 400.
     */
    private String resolveExtension(String contentType) {
        if (contentType == null) {
            throw new InvalidFileException("Content-Type ausente: tipo de archivo no permitido");
        }
        return switch (contentType.toLowerCase()) {
            case CONTENT_TYPE_JPEG -> ".jpg";
            case CONTENT_TYPE_PNG -> ".png";
            case CONTENT_TYPE_WEBP -> ".webp";
            default -> throw new InvalidFileException(
                    "Tipo de archivo no permitido: " + contentType
                            + ". Solo se aceptan image/jpeg, image/png e image/webp");
        };
    }
}
