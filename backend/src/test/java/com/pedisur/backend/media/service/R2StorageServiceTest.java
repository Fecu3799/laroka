package com.pedisur.backend.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pedisur.backend.media.config.R2Config;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    private static final String BUCKET = "laroka-media";
    private static final String PUBLIC_URL = "https://pub-xxxx.r2.dev";

    @Mock
    private S3Client r2S3Client;

    @Mock
    private R2Config r2Config;

    private R2StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new R2StorageService(r2S3Client, r2Config);
    }

    @Test
    void uploadPersistsOriginalNameAsMetadata() {
        when(r2Config.getBucketName()).thenReturn(BUCKET);
        when(r2Config.getPublicUrl()).thenReturn(PUBLIC_URL);
        when(r2S3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = storageService.upload(
                new byte[] { 1, 2, 3 }, "7/products/a.png", "image/png", "mi-foto.png");

        assertThat(url).isEqualTo(PUBLIC_URL + "/7/products/a.png");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verifyPutObject(captor);
        assertThat(captor.getValue().metadata()).containsEntry("original-name", "mi-foto.png");
    }

    @Test
    void uploadUrlEncodesNonAsciiOriginalName() {
        // Un nombre real con acentos/ñ/símbolos: sin el URL-encode, R2 firma la
        // cabecera de metadata con bytes no-ASCII y responde SignatureDoesNotMatch.
        String originalName = "piña & señóra €.png";
        when(r2Config.getBucketName()).thenReturn(BUCKET);
        when(r2Config.getPublicUrl()).thenReturn(PUBLIC_URL);
        when(r2S3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.upload(new byte[] { 1, 2, 3 }, "7/products/a.png", "image/png", originalName);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verifyPutObject(captor);
        String storedValue = captor.getValue().metadata().get("original-name");
        // Se guarda URL-encodeado y, por tanto, es puro ASCII (condición que hoy
        // fallaría sin el fix: se guardaría el nombre no-ASCII crudo).
        assertThat(storedValue).isEqualTo(URLEncoder.encode(originalName, StandardCharsets.UTF_8));
        assertThat(storedValue).isEqualTo(new String(storedValue.getBytes(StandardCharsets.US_ASCII),
                StandardCharsets.US_ASCII));
        assertThat(storedValue).doesNotContain("ñ", "€", "ó");
    }

    @Test
    void listDecodesNonAsciiOriginalName() {
        // El objeto tiene la metadata URL-encodeada (como la dejó upload): list debe
        // devolver el nombre original decodificado, no la cadena %XX cruda.
        String originalName = "piña & señóra €.png";
        String encoded = URLEncoder.encode(originalName, StandardCharsets.UTF_8);
        when(r2Config.getBucketName()).thenReturn(BUCKET);
        when(r2Config.getPublicUrl()).thenReturn(PUBLIC_URL);

        S3Object object = S3Object.builder()
                .key("7/products/a.png")
                .lastModified(Instant.EPOCH)
                .build();
        when(r2S3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(List.of(object)).build());
        when(r2S3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .metadata(Map.of("original-name", encoded))
                        .build());

        List<StoredObject> result = storageService.list("7/products/");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).originalName()).isEqualTo(originalName);
    }

    @Test
    void uploadWithoutOriginalNameSetsNoMetadata() {
        when(r2Config.getBucketName()).thenReturn(BUCKET);
        when(r2Config.getPublicUrl()).thenReturn(PUBLIC_URL);
        when(r2S3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.upload(new byte[] { 1 }, "7/products/a.png", "image/png", null);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verifyPutObject(captor);
        assertThat(captor.getValue().metadata()).isEmpty();
    }

    @Test
    void listReadsOriginalNameFromMetadata() {
        when(r2Config.getBucketName()).thenReturn(BUCKET);
        when(r2Config.getPublicUrl()).thenReturn(PUBLIC_URL);

        Instant uploadedAt = Instant.parse("2026-07-02T10:15:30Z");
        S3Object object = S3Object.builder()
                .key("7/products/a.png")
                .lastModified(uploadedAt)
                .build();
        when(r2S3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(List.of(object)).build());
        when(r2S3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .metadata(Map.of("original-name", "mi-foto.png"))
                        .build());

        List<StoredObject> result = storageService.list("7/products/");

        assertThat(result).hasSize(1);
        StoredObject stored = result.get(0);
        assertThat(stored.url()).isEqualTo(PUBLIC_URL + "/7/products/a.png");
        assertThat(stored.originalName()).isEqualTo("mi-foto.png");
        assertThat(stored.uploadedAt()).isEqualTo(uploadedAt);
    }

    @Test
    void listReturnsNullOriginalNameWhenMetadataAbsent() {
        when(r2Config.getBucketName()).thenReturn(BUCKET);
        when(r2Config.getPublicUrl()).thenReturn(PUBLIC_URL);

        S3Object object = S3Object.builder()
                .key("7/products/legacy.png")
                .lastModified(Instant.EPOCH)
                .build();
        when(r2S3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(List.of(object)).build());
        // Objeto subido antes de US-R2-01: sin metadata custom.
        when(r2S3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        List<StoredObject> result = storageService.list("7/products/");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).originalName()).isNull();
    }

    private void verifyPutObject(ArgumentCaptor<PutObjectRequest> captor) {
        org.mockito.Mockito.verify(r2S3Client).putObject(captor.capture(), any(RequestBody.class));
    }
}
