package com.laroka.backend.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.laroka.backend.auth.repository.RefreshTokenRepository;
import com.laroka.backend.auth.service.AuthService;
import com.laroka.backend.staffuser.controller.StaffUserController;
import com.laroka.backend.staffuser.mapper.StaffUserMapper;
import com.laroka.backend.staffuser.repository.StaffUserRepository;
import com.laroka.backend.staffuser.service.StaffUserService;

/**
 * US-SEC-01: verifica que los headers de seguridad HTTP estén presentes con su
 * valor exacto en toda respuesta, tanto en rutas públicas como protegidas.
 *
 * Los headers los escribe el HeaderWriterFilter de Spring Security al inicio de
 * la cadena de filtros, por lo que aparecen sin importar el status final de la
 * respuesta (200 de ruta pública, 401 de ruta protegida sin token, etc).
 */
@WebMvcTest(controllers = {StaffUserController.class})
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
	"jwt.secret=test-secret-minimum-32-chars-for-hmac256-ok",
	"jwt.expiration=3600000",
	"cors.allowed-origins=http://localhost:5173"
})
class SecurityHeadersIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private TokenBlacklist tokenBlacklist;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private StaffUserService staffUserService;

	@MockitoBean
	private StaffUserMapper staffUserMapper;

	@MockitoBean
	private StaffUserRepository staffUserRepository;

	/**
	 * Afirma que la respuesta contiene todos los headers de seguridad con su
	 * valor exacto. Falla si falta alguno o si el valor no coincide.
	 */
	private void assertSecurityHeaders(ResultActions result) throws Exception {
		result
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
			.andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"))
			.andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"));
	}

	// --- ruta pública: GET /branches ---

	@Test
	void publicEndpoint_includesAllSecurityHeaders() throws Exception {
		assertSecurityHeaders(mockMvc.perform(get("/branches")));
	}

	// --- ruta protegida sin token: GET /backoffice/orders → 401 ---

	@Test
	void protectedEndpoint_includesAllSecurityHeaders() throws Exception {
		assertSecurityHeaders(mockMvc.perform(get("/backoffice/orders")));
	}
}
