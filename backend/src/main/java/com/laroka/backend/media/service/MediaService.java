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
 * Excepción: el contexto bug-reports usa una carpeta plana sin tenant
 * (bug-reports/{uuid}.{ext}) y no es listable desde la galería.
 * No persiste nada en base de datos: solo sube/lista y retorna URLs públicas.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CONTENT_TYPE_WEBP = "image/webp";

    /** Contexto de capturas de bug reports (US-17-F-03+): key plana sin tenant. */
    private static final String CONTEXT_BUG_REPORTS = "bug-reports";

    /**
     * Contextos válidos para UPLOAD. products/branches/logo se organizan por tenant
     * ({tenantId}/{context}/...); bug-reports usa una carpeta plana separada
     * (bug-reports/{uuid}.{ext}, sin tenant) y NO es listable desde la galería.
     */
    private static final Set<String> UPLOAD_CONTEXTS = Set.of("products", "branches", "logo", CONTEXT_BUG_REPORTS);

    /**
     * Contextos válidos para LIST (galería). Excluye bug-reports a propósito: no
     * tiene sentido reutilizar capturas de bugs desde una galería.
     */
    private static final Set<String> LIST_CONTEXTS = Set.of("products", "branches", "logo");

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
        validateUploadContext(context);

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("El archivo es obligatorio y no puede estar vacío");
        }

        String extension = resolveExtension(file.getContentType());

        long maxBytes = maxUploadSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new InvalidFileException(
                    "El archivo excede el tamaño máximo permitido de " + maxUploadSizeMb + " MB");
        }

        // bug-reports va a una carpeta plana separada (sin tenant): no se agrupa ni
        // se lista por tenant como las imágenes de catálogo/sucursal.
        String key = CONTEXT_BUG_REPORTS.equals(context)
                ? context + "/" + UUID.randomUUID() + extension
                : tenantId + "/" + context + "/" + UUID.randomUUID() + extension;

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new StorageException("No se pudo leer el archivo. Volvé a seleccionarlo e intentá de nuevo.", ex);
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
        validateListContext(context);
        // La barra final acota el listado a la subcarpeta exacta del contexto.
        String prefix = tenantId + "/" + context + "/";
        return storageService.list(prefix);
    }

    /**
     * Valida el contexto de un upload contra la lista cerrada de contextos subibles
     * (incluye bug-reports).
     */
    private void validateUploadContext(String context) {
        if (context == null || !UPLOAD_CONTEXTS.contains(context)) {
            throw new InvalidFileException(
                    "Contexto no permitido: " + context
                            + ". Valores válidos: " + UPLOAD_CONTEXTS);
        }
    }

    /**
     * Valida el contexto de un listado contra la lista cerrada de contextos listables
     * (excluye bug-reports, que no se reutiliza desde la galería).
     */
    private void validateListContext(String context) {
        if (context == null || !LIST_CONTEXTS.contains(context)) {
            throw new InvalidFileException(
                    "Contexto no permitido: " + context
                            + ". Valores válidos: " + LIST_CONTEXTS);
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
