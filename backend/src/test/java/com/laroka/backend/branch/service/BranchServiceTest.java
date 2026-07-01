package com.laroka.backend.branch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.entity.BranchSchedule;
import com.laroka.backend.branch.entity.BranchScheduleOverride;
import com.laroka.backend.branch.entity.WeekDay;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchQRRepository;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.branch.repository.BranchScheduleOverrideRepository;
import com.laroka.backend.branch.repository.BranchScheduleRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

	@Mock
	private BranchRepository branchRepository;

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private BranchQRRepository branchQrRepository;

	@Mock
	private BranchScheduleRepository branchScheduleRepository;

	@Mock
	private BranchScheduleOverrideRepository branchScheduleOverrideRepository;

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

	// --- US-15-02 / US-15-F-01: updateConfig (maxShiftDurationMinutes + patch parcial) ---

	@Test
	void updateConfig_updatesAllFields() {
		Branch existing = branch(tenant());
		when(branchRepository.existsByIdAndTenantId(1, 1)).thenReturn(true);
		when(branchRepository.findById(1)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		Branch result = service.updateConfig(1, 1, 480, "Sucursal Centro", "Nueva Dirección 999",
			"+542804999999", new BigDecimal("150.00"), new BigDecimal("50.00"), 45);

		assertThat(result.getName()).isEqualTo("Sucursal Centro");
		assertThat(result.getAddress()).isEqualTo("Nueva Dirección 999");
		assertThat(result.getPhone()).isEqualTo("+542804999999");
		assertThat(result.getDeliveryFee()).isEqualByComparingTo("150.00");
		assertThat(result.getServiceFee()).isEqualByComparingTo("50.00");
		assertThat(result.getEstimatedDeliveryMinutes()).isEqualTo(45);
		assertThat(result.getMaxShiftDurationMinutes()).isEqualTo(480);
		verify(branchRepository).save(existing);
	}

	@Test
	void updateConfig_nullOptionalFields_keepsExistingValues() {
		Branch existing = branch(tenant()); // name "Playa Unión", address "Av. Principal 123", phone "+542804123456"
		when(branchRepository.existsByIdAndTenantId(1, 1)).thenReturn(true);
		when(branchRepository.findById(1)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		Branch result = service.updateConfig(1, 1, 480, null, null, null, null, null, null);

		assertThat(result.getName()).isEqualTo("Playa Unión");
		assertThat(result.getAddress()).isEqualTo("Av. Principal 123");
		assertThat(result.getPhone()).isEqualTo("+542804123456");
		assertThat(result.getEstimatedDeliveryMinutes()).isEqualTo(30);
		assertThat(result.getMaxShiftDurationMinutes()).isEqualTo(480);
	}

	@Test
	void updateConfig_otherTenant_throws403() {
		when(branchRepository.existsByIdAndTenantId(1, 99)).thenReturn(false);

		assertThatThrownBy(() -> service.updateConfig(1, 99, 480, "x", "x", "y", null, null, null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	// --- US-13-07: schedule por sucursal (ADMIN) ---

	private BranchSchedule dayInput(WeekDay day, boolean active, LocalTime o1, LocalTime c1, LocalTime o2, LocalTime c2) {
		return BranchSchedule.builder()
			.dayOfWeek(day).active(active)
			.openTime(o1).closeTime(c1).openTime2(o2).closeTime2(c2)
			.build();
	}

	@Test
	void findScheduleByBranch_otherTenant_throws403() {
		when(branchRepository.existsByIdAndTenantId(1, 99)).thenReturn(false);

		assertThatThrownBy(() -> service.findScheduleByBranch(1, 99))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	@Test
	void upsertSchedule_otherTenant_throws403() {
		when(branchRepository.existsByIdAndTenantId(1, 99)).thenReturn(false);

		assertThatThrownBy(() -> service.upsertSchedule(1, 99, List.of()))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	@Test
	void upsertSchedule_activeWithoutHours_throws400() {
		when(branchRepository.existsByIdAndTenantId(1, 1)).thenReturn(true);
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch(tenant())));

		BranchSchedule day = dayInput(WeekDay.MON, true, null, null, null, null);

		assertThatThrownBy(() -> service.upsertSchedule(1, 1, List.of(day)))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void upsertSchedule_partialSecondFrame_throws400() {
		when(branchRepository.existsByIdAndTenantId(1, 1)).thenReturn(true);
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch(tenant())));

		// closeTime2 ausente con openTime2 presente.
		BranchSchedule day = dayInput(WeekDay.MON, true,
			LocalTime.of(10, 0), LocalTime.of(15, 0), LocalTime.of(19, 0), null);

		assertThatThrownBy(() -> service.upsertSchedule(1, 1, List.of(day)))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void upsertSchedule_valid_createsMissingDayAndReturnsAll() {
		Branch branch = branch(tenant());
		when(branchRepository.existsByIdAndTenantId(1, 1)).thenReturn(true);
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
		when(branchScheduleRepository.findByBranchIdAndDayOfWeek(1, WeekDay.MON)).thenReturn(Optional.empty());
		when(branchScheduleRepository.save(any(BranchSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
		BranchSchedule persisted = dayInput(WeekDay.MON, true, LocalTime.of(10, 0), LocalTime.of(15, 0), null, null);
		persisted.setBranch(branch);
		when(branchScheduleRepository.findByBranchId(1)).thenReturn(List.of(persisted));

		BranchSchedule input = dayInput(WeekDay.MON, true, LocalTime.of(10, 0), LocalTime.of(15, 0), null, null);
		List<BranchSchedule> result = service.upsertSchedule(1, 1, List.of(input));

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getDayOfWeek()).isEqualTo(WeekDay.MON);
		verify(branchScheduleRepository).save(any(BranchSchedule.class));
	}

	// --- isOpenAt (US-13-06: branch_schedule + overrides) ---

	private static final Integer BRANCH_ID = 1;
	private final LocalDate monday = LocalDate.of(2026, 6, 22); // lunes → WeekDay.MON

	private BranchSchedule schedule(boolean active, LocalTime o1, LocalTime c1, LocalTime o2, LocalTime c2) {
		return BranchSchedule.builder()
			.id(1).branch(branch(tenant())).dayOfWeek(WeekDay.MON)
			.active(active).openTime(o1).closeTime(c1).openTime2(o2).closeTime2(c2)
			.build();
	}

	private BranchScheduleOverride override(boolean active, LocalTime o1, LocalTime c1) {
		return BranchScheduleOverride.builder()
			.id(1).branch(branch(tenant())).date(monday)
			.active(active).openTime(o1).closeTime(c1).priority(0)
			.build();
	}

	@Test
	void isOpenAt_frame1_withinHours_returnsTrue() {
		when(branchScheduleOverrideRepository.findByBranchIdAndDate(BRANCH_ID, monday)).thenReturn(Optional.empty());
		when(branchScheduleRepository.findByBranchIdAndDayOfWeek(BRANCH_ID, WeekDay.MON))
			.thenReturn(Optional.of(schedule(true, LocalTime.of(10, 0), LocalTime.of(15, 0), null, null)));

		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(12, 0))).isTrue();
		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(16, 0))).isFalse();
	}

	@Test
	void isOpenAt_frame2_withinSecondShift_returnsTrue() {
		when(branchScheduleOverrideRepository.findByBranchIdAndDate(BRANCH_ID, monday)).thenReturn(Optional.empty());
		when(branchScheduleRepository.findByBranchIdAndDayOfWeek(BRANCH_ID, WeekDay.MON))
			.thenReturn(Optional.of(schedule(true,
				LocalTime.of(10, 0), LocalTime.of(15, 0),
				LocalTime.of(19, 0), LocalTime.of(23, 0))));

		// Cae en franja 2.
		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(20, 0))).isTrue();
		// Entre franjas → cerrado.
		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(17, 0))).isFalse();
	}

	@Test
	void isOpenAt_activeOverride_usesOverrideHours() {
		when(branchScheduleOverrideRepository.findByBranchIdAndDate(BRANCH_ID, monday))
			.thenReturn(Optional.of(override(true, LocalTime.of(18, 0), LocalTime.of(22, 0))));

		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(19, 0))).isTrue();
		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(12, 0))).isFalse();
	}

	@Test
	void isOpenAt_inactiveOverride_returnsFalse() {
		when(branchScheduleOverrideRepository.findByBranchIdAndDate(BRANCH_ID, monday))
			.thenReturn(Optional.of(override(false, null, null)));

		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(12, 0))).isFalse();
	}

	@Test
	void isOpenAt_noScheduleForDay_returnsFalse() {
		when(branchScheduleOverrideRepository.findByBranchIdAndDate(BRANCH_ID, monday)).thenReturn(Optional.empty());
		when(branchScheduleRepository.findByBranchIdAndDayOfWeek(BRANCH_ID, WeekDay.MON)).thenReturn(Optional.empty());

		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(12, 0))).isFalse();
	}

	@Test
	void isOpenAt_inactiveSchedule_returnsFalse() {
		when(branchScheduleOverrideRepository.findByBranchIdAndDate(BRANCH_ID, monday)).thenReturn(Optional.empty());
		when(branchScheduleRepository.findByBranchIdAndDayOfWeek(BRANCH_ID, WeekDay.MON))
			.thenReturn(Optional.of(schedule(false, null, null, null, null)));

		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(12, 0))).isFalse();
	}

	@Test
	void isOpenAt_activeScheduleNoFrames_openAllDay() {
		when(branchScheduleOverrideRepository.findByBranchIdAndDate(BRANCH_ID, monday)).thenReturn(Optional.empty());
		when(branchScheduleRepository.findByBranchIdAndDayOfWeek(BRANCH_ID, WeekDay.MON))
			.thenReturn(Optional.of(schedule(true, null, null, null, null)));

		assertThat(service.isOpenAt(BRANCH_ID, monday, LocalTime.of(3, 0))).isTrue();
	}

}
