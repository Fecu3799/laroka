package com.laroka.backend.media.service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.laroka.backend.media.exception.InvalidFileException;
import com.laroka.backend.media.exception.StorageException;

import lombok.RequiredArgsConstructor;

/**
 * Lógica de negocio del upload y listado de imágenes (US-15-01, US-R2-01).
 *
 * Valida tipo y tamaño del archivo, organiza la clave por tenant y contexto
 * ({tenantId}/{context}/{uuid}.{ext}) y delega la subida al {@link StorageService}.
 * No persiste nada en base de datos: solo sube/lista y retorna URLs públicas.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CONTENT_TYPE_WEBP = "image/webp";

    /**
     * Contextos válidos: subcarpetas cerradas bajo las que se organizan las
     * imágenes del tenant. Cualquier otro valor se rechaza con 400.
     */
    private static final Set<String> VALID_CONTEXTS = Set.of("products", "branches", "logo");

    private final StorageService storageService;

    @Value("${media.max-upload-size-mb:5}")
    private long maxUploadSizeMb;

    /**
     * Sube la imagen recibida al almacenamiento y retorna su URL pública.
     *
     * @param file     archivo multipart recibido (campo "file")
     * @param tenantId tenant del usuario autenticado (extraído del JWT)
     * @param context  subcarpeta de destino: products, branches o logo
     * @return URL pública del archivo subido
     * @throws InvalidFileException si el contexto no es válido, el tipo no es
     *                              permitido, el tamaño excede el máximo o el
     *                              archivo está vacío
     * @throws StorageException     si el almacenamiento falla
     */
    public String upload(MultipartFile file, Integer tenantId, String context) {
        validateContext(context);

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("El archivo es obligatorio y no puede estar vacío");
        }

        String extension = resolveExtension(file.getContentType());

        long maxBytes = maxUploadSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new InvalidFileException(
                    "El archivo excede el tamaño máximo permitido de " + maxUploadSizeMb + " MB");
        }

        String key = tenantId + "/" + context + "/" + UUID.randomUUID() + extension;

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new StorageException("No se pudo leer el archivo recibido", ex);
        }

        return storageService.upload(content, key, file.getContentType(), file.getOriginalFilename());
    }

    /**
     * Lista los objetos subidos por el tenant en la subcarpeta del contexto pedido.
     *
     * @param tenantId tenant del usuario autenticado (extraído del JWT)
     * @param context  subcarpeta a listar: products, branches o logo
     * @return objetos de esa subcarpeta; lista vacía si no hay ninguno
     * @throws InvalidFileException si el contexto no es válido
     * @throws StorageException     si el listado falla
     */
    public List<StoredObject> list(Integer tenantId, String context) {
        validateContext(context);
        // La barra final acota el listado a la subcarpeta exacta del contexto.
        String prefix = tenantId + "/" + context + "/";
        return storageService.list(prefix);
    }

    /**
     * Valida el contexto contra la lista cerrada de subcarpetas permitidas.
     */
    private void validateContext(String context) {
        if (context == null || !VALID_CONTEXTS.contains(context)) {
            throw new InvalidFileException(
                    "Contexto no permitido: " + context
                            + ". Valores válidos: " + VALID_CONTEXTS);
        }
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
