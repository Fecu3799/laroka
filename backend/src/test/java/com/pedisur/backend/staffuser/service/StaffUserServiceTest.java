package com.pedisur.backend.staffuser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.branch.exception.BranchNotFoundException;
import com.pedisur.backend.branch.repository.BranchRepository;
import com.pedisur.backend.staffuser.entity.StaffUser;
import com.pedisur.backend.staffuser.entity.UserRole;
import com.pedisur.backend.staffuser.exception.StaffUserNotFoundException;
import com.pedisur.backend.staffuser.repository.StaffUserRepository;
import com.pedisur.backend.tenant.entity.Tenant;

@ExtendWith(MockitoExtension.class)
class StaffUserServiceTest {

	private static final int TENANT_ID = 10;
	private static final int BRANCH_ID = 1;
	private static final int USER_ID = 5;

	@Mock private StaffUserRepository staffUserRepository;
	@Mock private BranchRepository branchRepository;
	@Mock private PasswordEncoder passwordEncoder;

	@InjectMocks
	private StaffUserService staffUserService;

	private Tenant tenant() {
		return Tenant.builder().id(TENANT_ID).name("La Roka").emailDomain("laroka.com").build();
	}

	private Branch branch() {
		return Branch.builder().id(BRANCH_ID).name("Playa Unión").tenant(tenant()).build();
	}

	private StaffUser existingUser(String name, String email) {
		return StaffUser.builder()
			.id(USER_ID)
			.name(name)
			.email(email)
			.passwordHash("$2a$10$hashed")
			.role(UserRole.STAFF)
			.branch(branch())
			.build();
	}

	private StaffUser newStaffUser(String name) {
		return StaffUser.builder()
			.name(name)
			.passwordHash("plainPassword123")
			.role(UserRole.STAFF)
			.branch(Branch.builder().id(BRANCH_ID).build())
			.build();
	}

	// ── US-11-02: create ────────────────────────────────────────────────────────

	@Test
	void create_generatesEmailFromName() {
		StaffUser staffUser = newStaffUser("Juan Perez");

		when(staffUserRepository.existsByEmail("juan.perez@laroka.com")).thenReturn(false);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch()));
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez@laroka.com");
		verify(staffUserRepository).save(any(StaffUser.class));
	}

	@Test
	void create_emailConflict_appendsNumericSuffix() {
		StaffUser staffUser = newStaffUser("Juan Perez");

		when(staffUserRepository.existsByEmail("juan.perez@laroka.com")).thenReturn(true);
		when(staffUserRepository.existsByEmail("juan.perez2@laroka.com")).thenReturn(false);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch()));
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez2@laroka.com");
	}

	@Test
	void create_multipleSuffixConflicts_incrementsUntilAvailable() {
		StaffUser staffUser = newStaffUser("Juan Perez");

		when(staffUserRepository.existsByEmail("juan.perez@laroka.com")).thenReturn(true);
		when(staffUserRepository.existsByEmail("juan.perez2@laroka.com")).thenReturn(true);
		when(staffUserRepository.existsByEmail("juan.perez3@laroka.com")).thenReturn(false);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch()));
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez3@laroka.com");
	}

	@Test
	void generateEmail_nameWithAccents_normalizes() {
		when(staffUserRepository.existsByEmail("maria.garcia@laroka.com")).thenReturn(false);

		assertThat(staffUserService.generateEmail("María García", "laroka.com")).isEqualTo("maria.garcia@laroka.com");
	}

	@Test
	void generateEmail_nameWithNTilde_normalizesToN() {
		when(staffUserRepository.existsByEmail("juan.ibanez@laroka.com")).thenReturn(false);

		assertThat(staffUserService.generateEmail("Juan Ibáñez", "laroka.com")).isEqualTo("juan.ibanez@laroka.com");
	}

	@Test
	void generateEmail_nameWithMixedSpecialChars_normalizes() {
		when(staffUserRepository.existsByEmail("jose.munoz@laroka.com")).thenReturn(false);

		assertThat(staffUserService.generateEmail("José Muñoz", "laroka.com")).isEqualTo("jose.munoz@laroka.com");
	}

	@Test
	void create_invalidBranch_throwsBranchNotFoundException() {
		StaffUser staffUser = newStaffUser("New Staff");

		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> staffUserService.create(staffUser))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void create_usesEmailDomainFromTenant() {
		Tenant customTenant = Tenant.builder().id(TENANT_ID).name("Otra Pizza").emailDomain("otrapizza.com").build();
		Branch customBranch = Branch.builder().id(BRANCH_ID).name("Local").tenant(customTenant).build();
		StaffUser staffUser = newStaffUser("Juan Perez");

		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(customBranch));
		when(staffUserRepository.existsByEmail("juan.perez@otrapizza.com")).thenReturn(false);
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez@otrapizza.com");
	}

	@Test
	void create_emailConflict_deduplicationRespectsTenantDomain() {
		Tenant customTenant = Tenant.builder().id(TENANT_ID).name("Otra Pizza").emailDomain("otrapizza.com").build();
		Branch customBranch = Branch.builder().id(BRANCH_ID).name("Local").tenant(customTenant).build();
		StaffUser staffUser = newStaffUser("Juan Perez");

		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(customBranch));
		when(staffUserRepository.existsByEmail("juan.perez@otrapizza.com")).thenReturn(true);
		when(staffUserRepository.existsByEmail("juan.perez2@otrapizza.com")).thenReturn(false);
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez2@otrapizza.com");
	}

	// ── US-11-03: update ────────────────────────────────────────────────────────

	@Test
	void update_nameChanged_regeneratesEmail() {
		StaffUser existing = existingUser("Juan Perez", "juan.perez@laroka.com");
		StaffUser patch = StaffUser.builder()
			.name("Juan Garcia")
			.role(UserRole.MANAGER)
			.branch(Branch.builder().id(BRANCH_ID).build())
			.build();

		when(staffUserRepository.findByIdWithBranchAndTenant(USER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.existsByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(true);
		when(staffUserRepository.existsByEmailAndIdNot("juan.garcia@laroka.com", USER_ID)).thenReturn(false);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch()));
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.update(USER_ID, TENANT_ID, patch);

		assertThat(result.getEmail()).isEqualTo("juan.garcia@laroka.com");
		assertThat(result.getName()).isEqualTo("Juan Garcia");
		assertThat(result.getRole()).isEqualTo(UserRole.MANAGER);
	}

	@Test
	void update_nameUnchanged_doesNotRegenerateEmail() {
		StaffUser existing = existingUser("Juan Perez", "juan.perez@laroka.com");
		StaffUser patch = StaffUser.builder()
			.name("Juan Perez")
			.role(UserRole.MANAGER)
			.branch(Branch.builder().id(BRANCH_ID).build())
			.build();

		when(staffUserRepository.findByIdWithBranchAndTenant(USER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.existsByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(true);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch()));
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.update(USER_ID, TENANT_ID, patch);

		assertThat(result.getEmail()).isEqualTo("juan.perez@laroka.com");
		verify(staffUserRepository, never()).existsByEmailAndIdNot(anyString(), anyInt());
	}

	@Test
	void update_branchFromAnotherTenant_throwsForbidden() {
		StaffUser existing = existingUser("Juan Perez", "juan.perez@laroka.com");
		int foreignBranchId = 99;
		StaffUser patch = StaffUser.builder()
			.name("Juan Perez")
			.role(UserRole.STAFF)
			.branch(Branch.builder().id(foreignBranchId).build())
			.build();

		when(staffUserRepository.findByIdWithBranchAndTenant(USER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.existsByIdAndTenantId(foreignBranchId, TENANT_ID)).thenReturn(false);

		assertThatThrownBy(() -> staffUserService.update(USER_ID, TENANT_ID, patch))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
				.isEqualTo(HttpStatus.FORBIDDEN.value()));
	}

	@Test
	void update_userNotFound_throwsNotFoundException() {
		StaffUser patch = StaffUser.builder()
			.name("Juan Perez")
			.role(UserRole.STAFF)
			.branch(Branch.builder().id(BRANCH_ID).build())
			.build();

		when(staffUserRepository.findByIdWithBranchAndTenant(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> staffUserService.update(99, TENANT_ID, patch))
			.isInstanceOf(StaffUserNotFoundException.class);
	}

	// ── US-11-05: setStatus ─────────────────────────────────────────────────────

	@Test
	void setStatus_deactivatesAnotherUser_succeeds() {
		int adminUserId = 99;
		StaffUser existing = existingUser("Juan Perez", "juan.perez@laroka.com");

		when(staffUserRepository.findByIdWithBranchAndTenant(USER_ID)).thenReturn(Optional.of(existing));
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		staffUserService.setStatus(USER_ID, TENANT_ID, adminUserId, false);

		assertThat(existing.isActive()).isFalse();
		verify(staffUserRepository).save(existing);
	}

	@Test
	void setStatus_selfDeactivation_throwsBadRequest() {
		StaffUser existing = existingUser("Juan Perez", "juan.perez@laroka.com");

		when(staffUserRepository.findByIdWithBranchAndTenant(USER_ID)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> staffUserService.setStatus(USER_ID, TENANT_ID, USER_ID, false))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
				.isEqualTo(HttpStatus.BAD_REQUEST.value()));
	}

	@Test
	void setStatus_selfActivation_succeeds() {
		int adminUserId = USER_ID;
		StaffUser existing = existingUser("Juan Perez", "juan.perez@laroka.com");
		existing.setActive(false);

		when(staffUserRepository.findByIdWithBranchAndTenant(USER_ID)).thenReturn(Optional.of(existing));
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		staffUserService.setStatus(USER_ID, TENANT_ID, adminUserId, true);

		assertThat(existing.isActive()).isTrue();
	}

	@Test
	void setStatus_userNotFound_throwsNotFoundException() {
		when(staffUserRepository.findByIdWithBranchAndTenant(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> staffUserService.setStatus(99, TENANT_ID, 1, false))
			.isInstanceOf(StaffUserNotFoundException.class);
	}

	// ── US-11-04: resetPassword ─────────────────────────────────────────────────

	@Test
	void resetPassword_hashesAndSavesNewPassword() {
		StaffUser existing = existingUser("Juan Perez", "juan.perez@laroka.com");

		when(staffUserRepository.findByIdWithBranchAndTenant(USER_ID)).thenReturn(Optional.of(existing));
		when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newHashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		staffUserService.resetPassword(USER_ID, TENANT_ID, "newPassword123");

		verify(passwordEncoder).encode("newPassword123");
		verify(staffUserRepository).save(any(StaffUser.class));
		assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$newHashed");
	}

	@Test
	void resetPassword_userNotFound_throwsNotFoundException() {
		when(staffUserRepository.findByIdWithBranchAndTenant(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> staffUserService.resetPassword(99, TENANT_ID, "newPassword123"))
			.isInstanceOf(StaffUserNotFoundException.class);
	}
}
