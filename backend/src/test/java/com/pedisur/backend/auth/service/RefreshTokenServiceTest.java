package com.pedisur.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pedisur.backend.auth.entity.RefreshToken;
import com.pedisur.backend.auth.exception.RefreshTokenInvalidException;
import com.pedisur.backend.auth.repository.RefreshTokenRepository;
import com.pedisur.backend.staffuser.entity.StaffUser;
import com.pedisur.backend.staffuser.repository.StaffUserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenServiceTest {

	@Autowired private AuthService authService;
	@Autowired private RefreshTokenRepository refreshTokenRepository;
	@Autowired private StaffUserRepository staffUserRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private PasswordEncoder passwordEncoder;

	@BeforeEach
	void insertTestUser() {
		cleanTestData();
		Integer tenantId = jdbcTemplate.queryForObject(
			"INSERT INTO tenant (name, email_domain) VALUES (?, ?) RETURNING id",
			Integer.class, "Test Tenant", "laroka.com");
		Integer branchId = jdbcTemplate.queryForObject(
			"INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?) RETURNING id",
			Integer.class, "Test Branch", "Test Address", tenantId);
		jdbcTemplate.update(
			"INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
			"Admin", "admin@laroka.com", passwordEncoder.encode("admin123"), "ADMIN", branchId);
	}

	@AfterEach
	void cleanUp() {
		cleanTestData();
	}

	private void cleanTestData() {
		jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE branch_id IN (SELECT id FROM branch WHERE name = ?))", "Test Branch");
		jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM orders WHERE branch_id IN (SELECT id FROM branch WHERE name = ?))", "Test Branch");
		jdbcTemplate.update("DELETE FROM payment WHERE order_id IN (SELECT id FROM orders WHERE branch_id IN (SELECT id FROM branch WHERE name = ?))", "Test Branch");
		jdbcTemplate.update("DELETE FROM orders WHERE branch_id IN (SELECT id FROM branch WHERE name = ?)", "Test Branch");
		jdbcTemplate.update("DELETE FROM work_shift_summary WHERE shift_id IN (SELECT id FROM work_shift WHERE branch_id IN (SELECT id FROM branch WHERE name = ?))", "Test Branch");
		jdbcTemplate.update("DELETE FROM refresh_tokens WHERE staff_user_id IN (SELECT id FROM staff_user WHERE branch_id IN (SELECT id FROM branch WHERE name = ?))", "Test Branch");
		jdbcTemplate.update("DELETE FROM work_shift WHERE branch_id IN (SELECT id FROM branch WHERE name = ?)", "Test Branch");
		jdbcTemplate.update("DELETE FROM branch_qr WHERE branch_id IN (SELECT id FROM branch WHERE name = ?)", "Test Branch");
		jdbcTemplate.update("DELETE FROM branch_product WHERE branch_id IN (SELECT id FROM branch WHERE name = ?)", "Test Branch");
		jdbcTemplate.update("DELETE FROM staff_user WHERE branch_id IN (SELECT id FROM branch WHERE name = ?)", "Test Branch");
		jdbcTemplate.update("DELETE FROM branch_order_sequence WHERE branch_id IN (SELECT id FROM branch WHERE name = ?)", "Test Branch");
		jdbcTemplate.update("DELETE FROM branch WHERE name = ?", "Test Branch");
		jdbcTemplate.update("DELETE FROM product WHERE tenant_id IN (SELECT id FROM tenant WHERE name = ?)", "Test Tenant");
		jdbcTemplate.update("DELETE FROM category WHERE tenant_id IN (SELECT id FROM tenant WHERE name = ?)", "Test Tenant");
		jdbcTemplate.update("DELETE FROM tenant WHERE name = ?", "Test Tenant");
	}

	private StaffUser savedUser() {
		return staffUserRepository.findByEmailWithBranchAndTenant("admin@laroka.com")
			.orElseThrow();
	}

	// --- login persiste el refresh token en DB ---

	@Test
	void login_exitoso_persisteRefreshTokenEnDB() {
		LoginTokens tokens = authService.login("admin@laroka.com", "admin123");

		String hash = AuthService.sha256(tokens.refreshToken());
		assertThat(refreshTokenRepository.findByTokenHash(hash)).isPresent();
	}

	// --- refresh exitoso rota el token ---

	@Test
	void refresh_tokenValido_retornaNuevosTokensYRevocaAnterior() {
		LoginTokens first = authService.login("admin@laroka.com", "admin123");
		String firstHash = AuthService.sha256(first.refreshToken());

		LoginTokens second = authService.refresh(first.refreshToken());

		// el primer refresh token está revocado
		assertThat(refreshTokenRepository.findByTokenHash(firstHash))
			.isPresent()
			.get()
			.extracting(RefreshToken::isRevoked)
			.isEqualTo(true);

		// los nuevos tokens son distintos
		assertThat(second.accessToken()).isNotBlank();
		assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
		// el nuevo refresh token está en DB y no está revocado
		String secondHash = AuthService.sha256(second.refreshToken());
		assertThat(refreshTokenRepository.findByTokenHash(secondHash))
			.isPresent()
			.get()
			.extracting(RefreshToken::isRevoked)
			.isEqualTo(false);
	}

	// --- refresh token expirado retorna 401 ---

	@Test
	void refresh_tokenExpirado_lanzaRefreshTokenInvalidException() {
		StaffUser user = savedUser();
		String rawToken = "expired-raw-token-123";
		RefreshToken expired = RefreshToken.builder()
			.tokenHash(AuthService.sha256(rawToken))
			.staffUser(user)
			.expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
			.revoked(false)
			.createdAt(Instant.now().minus(8, ChronoUnit.DAYS))
			.build();
		refreshTokenRepository.save(expired);

		assertThatThrownBy(() -> authService.refresh(rawToken))
			.isInstanceOf(RefreshTokenInvalidException.class);
	}

	// --- refresh token revocado retorna 401 ---

	@Test
	void refresh_tokenRevocado_lanzaRefreshTokenInvalidException() {
		LoginTokens tokens = authService.login("admin@laroka.com", "admin123");
		// revocar el token directamente en DB
		String hash = AuthService.sha256(tokens.refreshToken());
		refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
			rt.setRevoked(true);
			refreshTokenRepository.save(rt);
		});

		assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
			.isInstanceOf(RefreshTokenInvalidException.class);
	}
}
