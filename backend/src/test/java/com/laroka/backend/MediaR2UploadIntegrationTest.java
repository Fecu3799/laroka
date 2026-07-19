package com.laroka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laroka.backend.media.config.R2Config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * US-TEST-07 — Test de integración REAL (no mockeado) contra el bucket de test de
 * Cloudflare R2. Verifica el flujo completo de subida/listado atravesando el SDK
 * de S3 y el servicio R2 real, no un mock: es el único lugar donde se detectan el
 * bug de checksums del SDK ({@code RequestChecksumCalculation.WHEN_REQUIRED} en
 * {@link R2Config}) y el de nombres de archivo no-ASCII (URL-encoding de la
 * metadata en {@code R2StorageService}), que solo se reprodujeron probando contra
 * el servicio real.
 *
 * <p>Credenciales del bucket de test (separadas de dev/staging/prod): en local
 * viven en {@code application-local.yml} bajo {@code r2.test.*} (por eso se activa
 * el perfil {@code local}); en CI llegan por variables de entorno
 * {@code R2_TEST_*} desde GitHub Secrets. {@link TestPropertySource} apunta los
 * beans productivos {@code r2.*} (que consume {@link R2Config}) al bucket de test,
 * de modo que el endpoint real escribe/lee en {@code laroka-test} y nunca en dev.
 *
 * <p>Aislamiento y limpieza: cada corrida usa un {@code tenantId} aleatorio, así
 * dos ejecuciones concurrentes que compartan el bucket no se pisan. El
 * {@code @AfterEach} borra SIEMPRE todos los objetos del prefijo del tenant
 * (incluso si el test falla a mitad de camino, porque JUnit ejecuta los
 * {@code @AfterEach} tras un fallo), dejando el bucket limpio entre corridas.
 *
 * <p>Cubre: upload exitoso con contexto válido genera la key esperada; upload con
 * nombre de archivo no-ASCII (acentos, espacio angosto U+202F de capturas de
 * macOS) no falla por {@code SignatureDoesNotMatch}; el nombre original se
 * recupera decodificado en el listado ({@code GET /backoffice/media}); contexto
 * inválido retorna 400 sin llegar a golpear R2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// local: carga las credenciales r2.test.* de application-local.yml en desarrollo.
// test (último → gana sobre local): fija datasource/jwt del perfil de tests.
@ActiveProfiles({ "local", "test" })
// Los beans productivos (R2Config → r2S3Client) apuntan al bucket de test. Los
// valores r2.test.* se resuelven de application-local.yml (local) o de las env
// R2_TEST_* (CI); el fallback vacío deja la precondición fallar con mensaje claro.
@TestPropertySource(properties = {
    "r2.access-key=${r2.test.access-key:}",
    "r2.secret-key=${r2.test.secret-key:}",
    "r2.bucket-name=${r2.test.bucket-name:laroka-test}",
    "r2.endpoint=${r2.test.endpoint:}",
    "r2.public-url=${r2.test.public-url:}"
})
class MediaR2UploadIntegrationTest {

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";

    /**
     * Tenant aleatorio por corrida: aísla el prefijo de objetos de otras
     * ejecuciones que compartan el bucket de test (CI concurrente) y acota la
     * limpieza a lo que este test creó.
     */
    private static final int TENANT_ID = 100_000_000 + ThreadLocalRandom.current().nextInt(900_000_000);

    /**
     * Nombre con caracteres no-ASCII: acentos, ñ y el espacio angosto (narrow
     * no-break space, U+202F) que macOS inserta en los nombres de captura de
     * pantalla. Es exactamente el caso que rompía la firma de la metadata.
     */
    private static final String NON_ASCII_NAME = "Captura piña añejo (café).png";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired S3Client r2S3Client;
    @Autowired R2Config r2Config;

    @BeforeEach
    void assertR2TestBucketConfigured() {
        // Falla fuerte y claro si faltan las credenciales de test, en vez de
        // esconderlas tras un SignatureDoesNotMatch/502 más adelante.
        assertThat(r2Config.getAccessKey())
            .as("Faltan credenciales del bucket de test de R2. En local: cargá r2.test.* "
                + "en application-local.yml (perfil local). En CI: definí las env R2_TEST_* "
                + "desde GitHub Secrets.")
            .isNotBlank();
        assertThat(r2Config.getEndpoint()).as("r2.test.endpoint no configurado").isNotBlank();
        assertThat(r2Config.getPublicUrl()).as("r2.test.public-url no configurado").isNotBlank();
    }

    /**
     * Limpieza garantizada: borra todos los objetos del prefijo del tenant. Se
     * ejecuta siempre (JUnit corre @AfterEach incluso si el test falló), evitando
     * dejar basura en el bucket compartido entre corridas.
     */
    @AfterEach
    void cleanupUploadedObjects() {
        String prefix = TENANT_ID + "/";
        ListObjectsV2Response listing = r2S3Client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(r2Config.getBucketName())
            .prefix(prefix)
            .build());
        for (S3Object object : listing.contents()) {
            r2S3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(object.key())
                .build());
        }
    }

    // ── upload con contexto válido → key {tenantId}/{context}/{uuid}.{ext} ─────────

    @Test
    void upload_validContext_generatesExpectedKeyAndPublicUrl() throws Exception {
        String url = upload("products", "menu.jpg", "image/jpeg", "contenido-jpeg".getBytes(StandardCharsets.UTF_8));

        // La URL pública se arma como {public-url}/{tenantId}/{context}/{uuid}.{ext}.
        String expectedPrefix = trimTrailingSlash(r2Config.getPublicUrl()) + "/" + TENANT_ID + "/products/";
        assertThat(url).startsWith(expectedPrefix).endsWith(".jpg");
        // El uuid entre el prefijo y la extensión no está vacío.
        String uuidPart = url.substring(expectedPrefix.length(), url.length() - ".jpg".length());
        assertThat(uuidPart).isNotBlank().doesNotContain("/");
    }

    // ── nombre no-ASCII: no rompe la firma y round-trip del original en el listado ─

    @Test
    void upload_nonAsciiFilename_succeedsAndRoundTripsOriginalName() throws Exception {
        // Si el URL-encoding de la metadata no estuviera, R2 respondería
        // SignatureDoesNotMatch → StorageException → 502. Llegar a 200 ya prueba el fix.
        upload("products", NON_ASCII_NAME, "image/png", "contenido-png".getBytes(StandardCharsets.UTF_8));

        // El listado hace HeadObject real y decodifica la metadata: el nombre
        // original debe volver idéntico, con acentos y el espacio angosto intactos.
        List<JsonNode> objects = listMedia("products");
        boolean roundTripped = objects.stream()
            .anyMatch(node -> NON_ASCII_NAME.equals(node.path("originalName").asText(null)));
        assertThat(roundTripped)
            .as("El nombre original no-ASCII debe recuperarse decodificado en GET /backoffice/media")
            .isTrue();
    }

    // ── contexto inválido → 400 sin llegar a R2 ───────────────────────────────────

    @Test
    void upload_invalidContext_returns400() throws Exception {
        // MediaService valida el contexto ANTES de invocar el StorageService, así
        // que un contexto inválido nunca golpea R2: se rechaza con 400 en la capa
        // de negocio.
        mockMvc.perform(multipart("/backoffice/media/upload")
                .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", "x".getBytes(StandardCharsets.UTF_8)))
                .param("context", "not-a-valid-context")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest());

        // Confirmación de que no se creó nada bajo el tenant: el bucket sigue vacío
        // para este prefijo (además del @AfterEach, que limpiaría igual).
        ListObjectsV2Response listing = r2S3Client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(r2Config.getBucketName())
            .prefix(TENANT_ID + "/")
            .build());
        assertThat(listing.contents()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    /** Sube un archivo al endpoint real y devuelve la URL pública retornada. */
    private String upload(String context, String filename, String contentType, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/backoffice/media/upload")
                .file(new MockMultipartFile("file", filename, contentType, content))
                .param("context", context)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
    }

    /** Lista las imágenes del contexto para el tenant del token y devuelve los nodos JSON. */
    private List<JsonNode> listMedia(String context) throws Exception {
        MvcResult result = mockMvc.perform(get("/backoffice/media")
                .param("context", context)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString());
        return objectMapper.convertValue(array, new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() { });
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Token ADMIN con tenantId (sin branchId): habilita upload/list de media. */
    private String adminToken() {
        return Jwts.builder()
            .subject("1")
            .claim("role", "ADMIN")
            .claim("tenantId", TENANT_ID)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
