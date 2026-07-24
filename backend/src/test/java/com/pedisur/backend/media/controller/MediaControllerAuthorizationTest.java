package com.pedisur.backend.media.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pedisur.backend.media.service.MediaService;
import com.pedisur.backend.shared.security.JwtAuthenticationFilter;
import com.pedisur.backend.shared.security.JwtService;
import com.pedisur.backend.shared.security.SecurityConfig;
import com.pedisur.backend.shared.security.TokenBlacklist;
import com.pedisur.backend.staffuser.service.StaffUserService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Autorización del upload de media (US-17): products/branches/logo son exclusivos
 * de ADMIN/MANAGER; bug-reports lo puede subir también STAFF. Ejercita la pila de
 * seguridad real (SecurityConfig + JwtAuthenticationFilter + @PreAuthorize).
 */
@WebMvcTest(controllers = MediaController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
    "jwt.secret=test-secret-minimum-32-chars-for-hmac256-ok",
    "jwt.expiration=3600000",
    "cors.allowed-origins=http://localhost:5173"
})
class MediaControllerAuthorizationTest {

    private static final String TEST_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int USER_ID = 1;
    private static final String UPLOAD_URL = "/backoffice/media/upload";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private TokenBlacklist tokenBlacklist;

    @MockitoBean
    private StaffUserService staffUserService;

    @BeforeEach
    void setUp() {
        // El JwtAuthenticationFilter valida que el usuario del token siga activo.
        when(staffUserService.isActive(USER_ID)).thenReturn(Optional.of(true));
    }

    private String tokenWithRole(String role) {
        return Jwts.builder()
                .subject(String.valueOf(USER_ID))
                .claim("role", role)
                .claim("branchId", 1)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "shot.png", "image/png", new byte[] { 1, 2, 3 });
    }

    // --- STAFF puede subir al contexto bug-reports → 200 ---

    @Test
    void staffToken_uploadBugReports_returns200() throws Exception {
        when(mediaService.upload(any(), any(), eq("bug-reports")))
                .thenReturn("https://pub-xxxx.r2.dev/bug-reports/abc.png");

        mockMvc.perform(multipart(UPLOAD_URL)
                .file(pngFile())
                .param("context", "bug-reports")
                .header("Authorization", "Bearer " + tokenWithRole("STAFF")))
            .andExpect(status().isOk());

        verify(mediaService).upload(any(), any(), eq("bug-reports"));
    }

    // --- STAFF NO puede subir a products → 403 (sigue exclusivo ADMIN/MANAGER) ---

    @Test
    void staffToken_uploadProducts_returns403() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL)
                .file(pngFile())
                .param("context", "products")
                .header("Authorization", "Bearer " + tokenWithRole("STAFF")))
            .andExpect(status().isForbidden());

        verify(mediaService, never()).upload(any(), any(), anyString());
    }

    // --- ADMIN sigue pudiendo subir a products → 200 (no se rompió lo existente) ---

    @Test
    void adminToken_uploadProducts_returns200() throws Exception {
        when(mediaService.upload(any(), any(), eq("products")))
                .thenReturn("https://pub-xxxx.r2.dev/7/products/abc.png");

        mockMvc.perform(multipart(UPLOAD_URL)
                .file(pngFile())
                .param("context", "products")
                .header("Authorization", "Bearer " + tokenWithRole("ADMIN")))
            .andExpect(status().isOk());

        verify(mediaService).upload(any(), any(), eq("products"));
    }
}
