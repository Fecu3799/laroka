package com.laroka.backend.shared.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.laroka.backend.auth.controller.AuthController;
import com.laroka.backend.auth.repository.RefreshTokenRepository;
import com.laroka.backend.auth.service.AuthService;
import com.laroka.backend.auth.service.LoginTokens;
import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.staffuser.controller.StaffUserController;
import com.laroka.backend.staffuser.dto.StaffUserResponseDTO;
import com.laroka.backend.staffuser.entity.StaffUser;
import com.laroka.backend.staffuser.entity.UserRole;
import com.laroka.backend.staffuser.mapper.StaffUserMapper;
import com.laroka.backend.staffuser.repository.StaffUserRepository;
import com.laroka.backend.staffuser.service.StaffUserService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = {AuthController.class, StaffUserController.class})
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
	"jwt.secret=test-secret-minimum-32-chars-for-hmac256-ok",
	"jwt.expiration=3600000",
	"cors.allowed-origins=http://localhost:5173"
})
class SecurityIntegrationTest {

	private static final String TEST_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private TokenBlacklist tokenBlacklist;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private StaffUserRepository staffUserRepository;

	@MockitoBean
	private StaffUserService staffUserService;

	@MockitoBean
	private StaffUserMapper staffUserMapper;

	// --- helpers ---

	private String tokenWithRole(String role) {
		return Jwts.builder()
			.subject("1")
			.claim("role", role)
			.claim("branchId", 1)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 3_600_000))
			.signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	private String expiredToken() {
		return Jwts.builder()
			.subject("1")
			.claim("role", "ADMIN")
			.claim("branchId", 1)
			.issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
			.expiration(new Date(System.currentTimeMillis() - 3_600_000))
			.signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	// --- usuario desactivado con JWT válido → 401 ---

	@Test
	void inactiveUser_validToken_returns401Desactivado() throws Exception {
		when(staffUserRepository.findActiveById(1)).thenReturn(Optional.of(false));

		mockMvc.perform(post("/backoffice/staff-users")
				.header("Authorization", "Bearer " + tokenWithRole("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Usuario desactivado"));
	}

	// --- backoffice endpoint sin token → 401 ---

	@Test
	void noToken_backofficeRequest_returns401() throws Exception {
		mockMvc.perform(post("/backoffice/staff-users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
	}

	// --- token expirado → 401 con "Session expired" ---

	@Test
	void expiredToken_backofficeRequest_returns401SessionExpired() throws Exception {
		mockMvc.perform(post("/backoffice/staff-users")
				.header("Authorization", "Bearer " + expiredToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Session expired"));
	}

	// --- token válido con rol STAFF en endpoint ADMIN → 403 ---

	@Test
	void staffToken_adminEndpoint_returns403() throws Exception {
		mockMvc.perform(post("/backoffice/staff-users")
				.header("Authorization", "Bearer " + tokenWithRole("STAFF"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"x\",\"email\":\"x@x.com\",\"password\":\"pass\",\"role\":\"STAFF\",\"branchId\":1}"))
			.andExpect(status().isForbidden());
	}

	// --- token ADMIN válido accede correctamente → 201 ---

	@Test
	void adminToken_adminEndpoint_returns201() throws Exception {
		StaffUser mockUser = StaffUser.builder()
			.id(2).name("Nuevo").email("nuevo@laroka.com")
			.role(UserRole.STAFF).branch(Branch.builder().id(1).build())
			.build();
		StaffUserResponseDTO responseDTO = StaffUserResponseDTO.builder()
			.id(2).name("Nuevo").email("nuevo@laroka.com")
			.role(UserRole.STAFF).branchId(1)
			.build();

		when(staffUserMapper.toEntity(any())).thenReturn(mockUser);
		when(staffUserService.create(any())).thenReturn(mockUser);
		when(staffUserMapper.toResponseDTO(any())).thenReturn(responseDTO);

		mockMvc.perform(post("/backoffice/staff-users")
				.header("Authorization", "Bearer " + tokenWithRole("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Nuevo\",\"email\":\"nuevo@laroka.com\",\"password\":\"pass123\",\"role\":\"STAFF\",\"branchId\":1}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("nuevo@laroka.com"));
	}

	// --- smoke: POST /auth/login es público (no requiere token) ---

	@Test
	void loginEndpoint_noToken_isPublicReturns200() throws Exception {
		when(authService.login(anyString(), anyString()))
			.thenReturn(new LoginTokens("mocked.jwt.token", "mocked-refresh-token"));

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@laroka.com\",\"password\":\"admin123\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value("mocked.jwt.token"))
			.andExpect(jsonPath("$.refreshToken").value("mocked-refresh-token"));
	}

	// --- smoke: GET /branches/** es público (no se bloquea por seguridad) ---

	@Test
	void branchesEndpoint_noToken_notBlocked() throws Exception {
		// Sin controlador activo en este contexto retorna 404, no 401.
		// Esto verifica que la capa de seguridad no bloquea la ruta.
		mockMvc.perform(get("/branches"))
			.andExpect(result -> {
				int status = result.getResponse().getStatus();
				if (status == 401) {
					throw new AssertionError("Expected public route, got 401 Unauthorized");
				}
			});
	}
}
