package com.pedisur.backend.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.pedisur.backend.media.exception.InvalidFileException;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final Integer TENANT_ID = 7;
    private static final String CONTEXT = "products";

    @Mock
    private StorageService storageService;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(storageService);
        ReflectionTestUtils.setField(mediaService, "maxUploadSizeMb", 5L);
    }

    @Test
    void uploadRejectsInvalidContext() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> mediaService.upload(file, TENANT_ID, "avatars"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Contexto no permitido");

        verify(storageService, never()).upload(any(), anyString(), anyString(), any());
    }

    @Test
    void uploadRejectsNullContext() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> mediaService.upload(file, TENANT_ID, null))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Contexto no permitido");

        verify(storageService, never()).upload(any(), anyString(), anyString(), any());
    }

    @Test
    void uploadRejectsInvalidContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> mediaService.upload(file, TENANT_ID, CONTEXT))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Tipo de archivo no permitido");

        verify(storageService, never()).upload(any(), anyString(), anyString(), any());
    }

    @Test
    void uploadRejectsFileExceedingMaxSize() {
        // 6 MB con el límite en 5 MB.
        byte[] tooBig = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", tooBig);

        assertThatThrownBy(() -> mediaService.upload(file, TENANT_ID, CONTEXT))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("tamaño máximo");

        verify(storageService, never()).upload(any(), anyString(), anyString(), any());
    }

    @Test
    void uploadBuildsKeyWithTenantAndContextAndForwardsOriginalName() {
        byte[] content = new byte[] { 10, 20, 30 };
        MockMultipartFile file = new MockMultipartFile(
                "file", "mi-foto.png", "image/png", content);

        String expectedUrl = "https://pub-xxxx.r2.dev/7/products/some-uuid.png";
        when(storageService.upload(any(), anyString(), eq("image/png"), anyString()))
                .thenReturn(expectedUrl);

        String result = mediaService.upload(file, TENANT_ID, CONTEXT);

        assertThat(result).isEqualTo(expectedUrl);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(eq(content), keyCaptor.capture(), eq("image/png"), nameCaptor.capture());
        // La clave se organiza por tenant y contexto, y termina con la extensión derivada del tipo.
        assertThat(keyCaptor.getValue())
                .startsWith(TENANT_ID + "/" + CONTEXT + "/")
                .endsWith(".png");
        // El nombre original se reenvía al storage para guardarlo como metadata.
        assertThat(nameCaptor.getValue()).isEqualTo("mi-foto.png");
    }

    @Test
    void uploadBugReportsUsesFlatKeyWithoutTenant() {
        byte[] content = new byte[] { 5, 6, 7 };
        MockMultipartFile file = new MockMultipartFile(
                "file", "captura.png", "image/png", content);

        String expectedUrl = "https://pub-xxxx.r2.dev/bug-reports/some-uuid.png";
        when(storageService.upload(any(), anyString(), eq("image/png"), anyString()))
                .thenReturn(expectedUrl);

        String result = mediaService.upload(file, TENANT_ID, "bug-reports");

        assertThat(result).isEqualTo(expectedUrl);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(eq(content), keyCaptor.capture(), eq("image/png"), anyString());
        // Carpeta plana: empieza con "bug-reports/" y NO lleva el tenant como prefijo.
        assertThat(keyCaptor.getValue())
                .startsWith("bug-reports/")
                .doesNotStartWith(TENANT_ID + "/")
                .endsWith(".png");
    }

    @Test
    void listRejectsBugReportsContext() {
        // bug-reports es subible pero NO listable desde la galería.
        assertThatThrownBy(() -> mediaService.list(TENANT_ID, "bug-reports"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Contexto no permitido");

        verify(storageService, never()).list(anyString());
    }

    @Test
    void listRejectsInvalidContext() {
        assertThatThrownBy(() -> mediaService.list(TENANT_ID, "avatars"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Contexto no permitido");

        verify(storageService, never()).list(anyString());
    }

    @Test
    void listFiltersByTenantAndContextSubfolder() {
        StoredObject stored = new StoredObject(
                "https://pub-xxxx.r2.dev/7/products/a.png", "a.png", Instant.EPOCH);
        when(storageService.list(anyString())).thenReturn(List.of(stored));

        List<StoredObject> result = mediaService.list(TENANT_ID, CONTEXT);

        assertThat(result).containsExactly(stored);
        // El prefijo acota a la subcarpeta exacta del contexto (con barra final).
        verify(storageService).list(TENANT_ID + "/" + CONTEXT + "/");
    }
}
