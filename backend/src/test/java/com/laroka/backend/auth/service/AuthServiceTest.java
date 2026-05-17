package com.laroka.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.laroka.backend.auth.exception.InvalidCredentialsException;
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

	@Test
	void login_validCredentials_returnsToken() {
		StaffUser user = staffUser();
		when(staffUserRepository.findByEmailWithBranchAndTenant("admin@laroka.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("admin123", user.getPasswordHash())).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("jwt.token.here");

		String token = authService.login("admin@laroka.com", "admin123");

		assertThat(token).isEqualTo("jwt.token.here");
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
}
