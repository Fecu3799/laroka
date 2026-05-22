package com.laroka.backend.branch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

	@Mock
	private BranchRepository branchRepository;

	@Mock
	private TenantRepository tenantRepository;

	@InjectMocks
	private BranchService service;

	private Tenant tenant() {
		return Tenant.builder().id(1).name("LaRoka").build();
	}

	private Branch branch(Tenant tenant) {
		return Branch.builder().id(1).name("Playa Unión").address("Av. Principal 123")
			.estimatedDeliveryMinutes(30).phone("+542804123456").tenant(tenant).build();
	}

	@Test
	void findById_returnsExistingBranch() {
		Branch branch = branch(tenant());
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

		Branch result = service.findById(1);

		assertThat(result.getId()).isEqualTo(1);
		assertThat(result.getName()).isEqualTo("Playa Unión");
	}

	@Test
	void findById_notFound_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(99))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void findAll_returnsAllBranches() {
		Tenant p = tenant();
		when(branchRepository.findAll()).thenReturn(List.of(branch(p), branch(p)));

		List<Branch> result = service.findAll();

		assertThat(result).hasSize(2);
	}

	@Test
	void findByTenant_validTenant_returnsBranches() {
		Tenant p = tenant();
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(branchRepository.findByTenantId(1)).thenReturn(List.of(branch(p)));

		List<Branch> result = service.findByTenant(1);

		assertThat(result).hasSize(1);
	}

	@Test
	void findByTenant_invalidTenant_throwsTenantNotFoundException() {
		when(tenantRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByTenant(99))
			.isInstanceOf(TenantNotFoundException.class);
	}

	@Test
	void create_validBranch_savesAndReturns() {
		Tenant p = tenant();
		Branch branch = branch(p);
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(branchRepository.save(any(Branch.class))).thenReturn(branch);

		Branch result = service.create(branch);

		assertThat(result.getName()).isEqualTo("Playa Unión");
		verify(branchRepository).save(branch);
	}

	@Test
	void create_invalidTenant_throwsTenantNotFoundException() {
		Branch branch = Branch.builder().id(1).name("Test").address("Addr")
			.estimatedDeliveryMinutes(30).phone("+54280000000").tenant(Tenant.builder().id(99).build()).build();
		when(tenantRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(branch))
			.isInstanceOf(TenantNotFoundException.class);
	}

	@Test
	void update_existingBranch_updatesAndReturns() {
		Tenant p = tenant();
		Branch existing = branch(p);
		Branch updates = Branch.builder().name("Rawson").address("Nueva 456")
			.estimatedDeliveryMinutes(25).phone("+542804234567").tenant(p).build();
		when(branchRepository.findById(1)).thenReturn(Optional.of(existing));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(branchRepository.save(any(Branch.class))).thenReturn(existing);

		Branch result = service.update(1, updates);

		assertThat(result.getName()).isEqualTo("Rawson");
	}

	@Test
	void update_notFound_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99, branch(tenant())))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void delete_existingBranch_deletes() {
		Branch branch = branch(tenant());
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

		service.delete(1);

		verify(branchRepository).delete(branch);
	}

	@Test
	void delete_notFound_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99))
			.isInstanceOf(BranchNotFoundException.class);
	}

	// --- isOpenAt (US-06-08) ---

	private Branch scheduledbranch(String openDays, LocalTime opening, LocalTime closing) {
		return Branch.builder()
			.id(1).name("Playa Unión").address("Av. Principal 123")
			.estimatedDeliveryMinutes(30).phone("+542804123456")
			.openingTime(opening).closingTime(closing).openDays(openDays)
			.tenant(tenant())
			.build();
	}

	private LocalDate monday()   { return LocalDate.now().with(DayOfWeek.MONDAY); }
	private LocalDate saturday() { return LocalDate.now().with(DayOfWeek.SATURDAY); }
	private LocalDate sunday()   { return LocalDate.now().with(DayOfWeek.SUNDAY); }

	@Test
	void isOpenAt_duringOpenHoursOnOpenDay_returnsTrue() {
		Branch branch = scheduledbranch("LUN,MAR,MIE,JUE,VIE,SAB",
			LocalTime.of(10, 0), LocalTime.of(23, 0));

		assertThat(BranchService.isOpenAt(branch, monday(), LocalTime.of(13, 0))).isTrue();
	}

	@Test
	void isOpenAt_beforeOpeningTimeOnOpenDay_returnsFalse() {
		Branch branch = scheduledbranch("LUN,MAR,MIE,JUE,VIE,SAB",
			LocalTime.of(10, 0), LocalTime.of(23, 0));

		assertThat(BranchService.isOpenAt(branch, monday(), LocalTime.of(9, 59))).isFalse();
	}

	@Test
	void isOpenAt_afterClosingTimeOnOpenDay_returnsFalse() {
		Branch branch = scheduledbranch("LUN,MAR,MIE,JUE,VIE,SAB",
			LocalTime.of(10, 0), LocalTime.of(22, 0));

		assertThat(BranchService.isOpenAt(branch, monday(), LocalTime.of(22, 1))).isFalse();
	}

	@Test
	void isOpenAt_exactlyAtOpeningTime_returnsTrue() {
		Branch branch = scheduledbranch("LUN,MAR,MIE,JUE,VIE,SAB",
			LocalTime.of(10, 0), LocalTime.of(23, 0));

		assertThat(BranchService.isOpenAt(branch, monday(), LocalTime.of(10, 0))).isTrue();
	}

	@Test
	void isOpenAt_exactlyAtClosingTime_returnsTrue() {
		Branch branch = scheduledbranch("LUN,MAR,MIE,JUE,VIE,SAB",
			LocalTime.of(10, 0), LocalTime.of(23, 0));

		assertThat(BranchService.isOpenAt(branch, monday(), LocalTime.of(23, 0))).isTrue();
	}

	@Test
	void isOpenAt_duringOpenHoursOnClosedDay_returnsFalse() {
		Branch branch = scheduledbranch("MAR,MIE,JUE,VIE,SAB,DOM",
			LocalTime.of(10, 0), LocalTime.of(23, 0));

		assertThat(BranchService.isOpenAt(branch, monday(), LocalTime.of(13, 0))).isFalse();
	}

	@Test
	void isOpenAt_openOnWeekend_returnsTrue() {
		Branch branch = scheduledbranch("LUN,MAR,MIE,JUE,VIE,SAB,DOM",
			LocalTime.of(10, 0), LocalTime.of(23, 0));

		assertThat(BranchService.isOpenAt(branch, saturday(), LocalTime.of(20, 0))).isTrue();
		assertThat(BranchService.isOpenAt(branch, sunday(), LocalTime.of(20, 0))).isTrue();
	}

	@Test
	void isOpen_delegatesToFindById() {
		Branch branch = scheduledbranch("LUN,MAR,MIE,JUE,VIE,SAB,DOM",
			LocalTime.of(0, 0), LocalTime.of(23, 59));
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

		assertThat(service.isOpen(1)).isTrue();
	}

}
