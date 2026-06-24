package com.laroka.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.laroka.backend.auth.exception.InvalidCredentialsException;
import com.laroka.backend.auth.repository.RefreshTokenRepository;
import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.shared.security.JwtService;
import com.laroka.backend.staffuser.entity.StaffUser;
import com.laroka.backend.staffuser.entity.UserRole;
import com.laroka.backend.staffuser.repository.StaffUserRepository;
import com.laroka.backend.tenant.entity.Tenant;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock private StaffUserRepository staffUserRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private JwtService jwtService;
	@Mock private RefreshTokenRepository refreshTokenRepository;

	@InjectMocks
	private AuthService authService;

	private StaffUser staffUser() {
		Tenant tenant = Tenant.builder().id(1).name("LaRoka").build();
		Branch branch = Branch.builder().id(1).name("Playa Unión").tenant(tenant).build();
		return StaffUser.builder()
			.id(1)
			.name("Admin")
			.email("admin@laroka.com")
			.passwordHash("$2a$10$hashedpassword")
			.role(UserRole.ADMIN)
			.branch(branch)
			.build();
	}

	private StaffUser inactiveUser() {
		Tenant tenant = Tenant.builder().id(1).name("LaRoka").build();
		Branch branch = Branch.builder().id(1).name("Playa Unión").tenant(tenant).build();
		return StaffUser.builder()
			.id(2)
			.name("Inactive Staff")
			.email("inactive@laroka.com")
			.passwordHash("$2a$10$hashedpassword")
			.role(UserRole.STAFF)
			.active(false)
			.branch(branch)
			.build();
	}

	@Test
	void login_validCredentials_returnsBothTokens() {
		StaffUser user = staffUser();
		when(staffUserRepository.findByEmailWithBranchAndTenant("admin@laroka.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("admin123", user.getPasswordHash())).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("jwt.token.here");
		when(refreshTokenRepository.save(any())).thenReturn(null);

		LoginTokens tokens = authService.login("admin@laroka.com", "admin123");

		assertThat(tokens.accessToken()).isEqualTo("jwt.token.here");
		assertThat(tokens.refreshToken()).isNotBlank();
	}

	@Test
	void login_userNotFound_throwsInvalidCredentialsException() {
		when(staffUserRepository.findByEmailWithBranchAndTenant("unknown@laroka.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login("unknown@laroka.com", "anyPassword"))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Invalid credentials");
	}

	@Test
	void login_wrongPassword_throwsInvalidCredentialsException() {
		StaffUser user = staffUser();
		when(staffUserRepository.findByEmailWithBranchAndTenant("admin@laroka.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrongpassword", user.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> authService.login("admin@laroka.com", "wrongpassword"))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Invalid credentials");
	}

	@Test
	void login_inactiveUser_throwsInvalidCredentialsException() {
		StaffUser user = inactiveUser();
		when(staffUserRepository.findByEmailWithBranchAndTenant("inactive@laroka.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);

		assertThatThrownBy(() -> authService.login("inactive@laroka.com", "password123"))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Usuario desactivado");
	}
}
