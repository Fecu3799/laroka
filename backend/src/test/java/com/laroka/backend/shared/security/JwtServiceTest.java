package com.laroka.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-SEC-05: cubre el grace period de rotación de JWT_SECRET en JwtService.
 * Verifica que la validación acepte tokens firmados con el secret primario o
 * con el secundario (JWT_SECRET_PREVIOUS), y que rechace los firmados con un
 * secret desconocido.
 */
class JwtServiceTest {

	// Secretos de prueba: ≥32 bytes para HMAC-SHA256.
	private static final String PRIMARY_SECRET = "primary-secret-minimum-32-chars-for-hmac256";
	private static final String PREVIOUS_SECRET = "previous-secret-minimum-32-chars-for-hmac256";
	private static final String UNKNOWN_SECRET = "unknown-secret-minimum-32-chars-for-hmac256";

	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		jwtService = new JwtService();
		ReflectionTestUtils.setField(jwtService, "secret", PRIMARY_SECRET);
		ReflectionTestUtils.setField(jwtService, "secretPrevious", PREVIOUS_SECRET);
		ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);
	}

	@Test
	void validateToken_conSecretPrimario_esValido() {
		String token = tokenSignedWith(PRIMARY_SECRET);

		assertThat(jwtService.validateToken(token)).isTrue();
	}

	@Test
	void validateToken_conSecretSecundario_esValido() {
		String token = tokenSignedWith(PREVIOUS_SECRET);

		assertThat(jwtService.validateToken(token)).isTrue();
	}

	@Test
	void validateToken_conSecretDesconocido_esInvalido() {
		String token = tokenSignedWith(UNKNOWN_SECRET);

		assertThat(jwtService.validateToken(token)).isFalse();
	}

	private String tokenSignedWith(String secret) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + 3_600_000L);
		return Jwts.builder()
			.subject("1")
			.claim("role", "ADMIN")
			.claim("tenantId", 1)
			.issuedAt(now)
			.expiration(expiry)
			.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}
}
