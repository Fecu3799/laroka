package com.laroka.backend.branch.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BranchService {

	private final BranchRepository repository;
	private final TenantRepository tenantRepository;

	public Branch findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new BranchNotFoundException(id));
	}

	public List<Branch> findByTenant(Integer tenantId) {
		validateTenantExists(tenantId);
		return repository.findByTenantId(tenantId);
	}

	public List<Branch> findAll() {
		return repository.findAll();
	}

	public Branch create(Branch branch) {
		Tenant tenant = validateTenantExists(branch.getTenant().getId());
		branch.setTenant(tenant);
		return repository.save(branch);
	}

	public Branch update(Integer id, Branch updates) {
		Branch branch = findById(id);
		Tenant tenant = validateTenantExists(updates.getTenant().getId());
		branch.setName(updates.getName());
		branch.setAddress(updates.getAddress());
		branch.setEstimatedDeliveryMinutes(updates.getEstimatedDeliveryMinutes());
		branch.setPhone(updates.getPhone());
		branch.setTenant(tenant);
		return repository.save(branch);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}

	public boolean isOpen(Integer branchId) {
		return isOpenAt(findById(branchId), LocalDate.now(), LocalTime.now());
	}

	static boolean isOpenAt(Branch branch, LocalDate date, LocalTime now) {
		String dayCode = DAY_CODES.get(date.getDayOfWeek());
		List<String> openDays = Arrays.asList(branch.getOpenDays().split(","));
		if (!openDays.contains(dayCode)) {
			return false;
		}
		return !now.isBefore(branch.getOpeningTime()) && !now.isAfter(branch.getClosingTime());
	}

	private static final Map<DayOfWeek, String> DAY_CODES = Map.of(
		DayOfWeek.MONDAY,    "LUN",
		DayOfWeek.TUESDAY,   "MAR",
		DayOfWeek.WEDNESDAY, "MIE",
		DayOfWeek.THURSDAY,  "JUE",
		DayOfWeek.FRIDAY,    "VIE",
		DayOfWeek.SATURDAY,  "SAB",
		DayOfWeek.SUNDAY,    "DOM"
	);

	private Tenant validateTenantExists(Integer tenantId) {
		return tenantRepository.findById(tenantId)
			.orElseThrow(() -> new TenantNotFoundException(tenantId));
	}
}
