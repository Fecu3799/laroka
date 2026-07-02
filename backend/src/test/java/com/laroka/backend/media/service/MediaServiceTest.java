package com.laroka.backend.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.laroka.backend.media.exception.InvalidFileException;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final Integer TENANT_ID = 7;

    @Mock
    private StorageService storageService;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(storageService);
        ReflectionTestUtils.setField(mediaService, "maxUploadSizeMb", 5L);
    }

    @Test
    void uploadRejectsInvalidContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> mediaService.upload(file, TENANT_ID))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Tipo de archivo no permitido");

        verify(storageService, never()).upload(any(), anyString(), anyString());
    }

    @Test
    void uploadRejectsFileExceedingMaxSize() {
        // 6 MB con el límite en 5 MB.
        byte[] tooBig = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", tooBig);

        assertThatThrownBy(() -> mediaService.upload(file, TENANT_ID))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("tamaño máximo");

        verify(storageService, never()).upload(any(), anyString(), anyString());
    }

    @Test
    void uploadReturnsPublicUrlOnSuccess() {
        byte[] content = new byte[] { 10, 20, 30 };
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", content);

        String expectedUrl = "https://pub-xxxx.r2.dev/7/some-uuid.png";
        when(storageService.upload(any(), anyString(), eq("image/png"))).thenReturn(expectedUrl);

        String result = mediaService.upload(file, TENANT_ID);

        assertThat(result).isEqualTo(expectedUrl);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(eq(content), keyCaptor.capture(), eq("image/png"));
        // La clave se organiza por tenant y termina con la extensión derivada del tipo.
        assertThat(keyCaptor.getValue())
                .startsWith(TENANT_ID + "/")
                .endsWith(".png");
    }
}
