package com.laroka.backend.staffuser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.staffuser.entity.StaffUser;
import com.laroka.backend.staffuser.entity.UserRole;
import com.laroka.backend.staffuser.repository.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class StaffUserServiceTest {

	@Mock private StaffUserRepository staffUserRepository;
	@Mock private BranchRepository branchRepository;
	@Mock private PasswordEncoder passwordEncoder;

	@InjectMocks
	private StaffUserService staffUserService;

	private Branch branch() {
		return Branch.builder().id(1).name("Playa Unión").build();
	}

	private StaffUser staffUser(String name) {
		return StaffUser.builder()
			.name(name)
			.passwordHash("plainPassword123")
			.role(UserRole.STAFF)
			.branch(branch())
			.build();
	}

	@Test
	void create_generatesEmailFromName() {
		StaffUser staffUser = staffUser("Juan Perez");

		when(staffUserRepository.existsByEmail("juan.perez@laroka.com")).thenReturn(false);
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch()));
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez@laroka.com");
		verify(staffUserRepository).save(any(StaffUser.class));
	}

	@Test
	void create_emailConflict_appendsNumericSuffix() {
		StaffUser staffUser = staffUser("Juan Perez");

		when(staffUserRepository.existsByEmail("juan.perez@laroka.com")).thenReturn(true);
		when(staffUserRepository.existsByEmail("juan.perez2@laroka.com")).thenReturn(false);
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch()));
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez2@laroka.com");
	}

	@Test
	void create_multipleSuffixConflicts_incrementsUntilAvailable() {
		StaffUser staffUser = staffUser("Juan Perez");

		when(staffUserRepository.existsByEmail("juan.perez@laroka.com")).thenReturn(true);
		when(staffUserRepository.existsByEmail("juan.perez2@laroka.com")).thenReturn(true);
		when(staffUserRepository.existsByEmail("juan.perez3@laroka.com")).thenReturn(false);
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch()));
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getEmail()).isEqualTo("juan.perez3@laroka.com");
	}

	@Test
	void generateEmail_nameWithAccents_normalizes() {
		when(staffUserRepository.existsByEmail("maria.garcia@laroka.com")).thenReturn(false);

		String email = staffUserService.generateEmail("María García");

		assertThat(email).isEqualTo("maria.garcia@laroka.com");
	}

	@Test
	void generateEmail_nameWithNTilde_normalizesToN() {
		when(staffUserRepository.existsByEmail("juan.ibanez@laroka.com")).thenReturn(false);

		String email = staffUserService.generateEmail("Juan Ibáñez");

		assertThat(email).isEqualTo("juan.ibanez@laroka.com");
	}

	@Test
	void generateEmail_nameWithMixedSpecialChars_normalizes() {
		when(staffUserRepository.existsByEmail("jose.munoz@laroka.com")).thenReturn(false);

		String email = staffUserService.generateEmail("José Muñoz");

		assertThat(email).isEqualTo("jose.munoz@laroka.com");
	}

	@Test
	void create_invalidBranch_throwsBranchNotFoundException() {
		StaffUser staffUser = staffUser("New Staff");

		when(staffUserRepository.existsByEmail(anyString())).thenReturn(false);
		when(branchRepository.findById(1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> staffUserService.create(staffUser))
			.isInstanceOf(BranchNotFoundException.class);
	}
}
