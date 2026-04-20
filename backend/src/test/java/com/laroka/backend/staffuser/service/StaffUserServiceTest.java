package com.laroka.backend.staffuser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.laroka.backend.shared.exception.BusinessException;
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

	private StaffUser staffUser() {
		return StaffUser.builder()
			.name("New Staff")
			.email("newstaff@laroka.com")
			.passwordHash("plainPassword123")
			.role(UserRole.STAFF)
			.branch(branch())
			.build();
	}

	@Test
	void create_validStaffUser_savesAndReturns() {
		StaffUser staffUser = staffUser();
		Branch branch = branch();

		when(staffUserRepository.findByEmail("newstaff@laroka.com")).thenReturn(Optional.empty());
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
		when(passwordEncoder.encode("plainPassword123")).thenReturn("$2a$10$hashedPassword");
		when(staffUserRepository.save(any(StaffUser.class))).thenReturn(staffUser);

		StaffUser result = staffUserService.create(staffUser);

		assertThat(result.getName()).isEqualTo("New Staff");
		assertThat(result.getEmail()).isEqualTo("newstaff@laroka.com");
		verify(staffUserRepository).save(any(StaffUser.class));
	}

	@Test
	void create_duplicateEmail_throwsBusinessException() {
		StaffUser staffUser = staffUser();

		when(staffUserRepository.findByEmail("newstaff@laroka.com")).thenReturn(Optional.of(staffUser));

		assertThatThrownBy(() -> staffUserService.create(staffUser))
			.isInstanceOf(BusinessException.class)
			.hasMessage("Email already in use");
	}

	@Test
	void create_invalidBranch_throwsBranchNotFoundException() {
		StaffUser staffUser = staffUser();

		when(staffUserRepository.findByEmail("newstaff@laroka.com")).thenReturn(Optional.empty());
		when(branchRepository.findById(1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> staffUserService.create(staffUser))
			.isInstanceOf(BranchNotFoundException.class);
	}
}
