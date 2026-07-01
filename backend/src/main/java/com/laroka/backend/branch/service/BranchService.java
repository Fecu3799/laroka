package com.laroka.backend.branch.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.entity.BranchQR;
import com.laroka.backend.branch.entity.BranchSchedule;
import com.laroka.backend.branch.entity.BranchScheduleOverride;
import com.laroka.backend.branch.entity.WeekDay;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchQRRepository;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.branch.repository.BranchScheduleOverrideRepository;
import com.laroka.backend.branch.repository.BranchScheduleRepository;
import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.repository.WorkShiftRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BranchService {

	private final BranchRepository repository;
	private final TenantRepository tenantRepository;
	private final BranchQRRepository branchQrRepository;
	private final BranchScheduleRepository branchScheduleRepository;
	private final BranchScheduleOverrideRepository branchScheduleOverrideRepository;
	private final WorkShiftRepository workShiftRepository;

	public Branch findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new BranchNotFoundException(id));
	}

	public List<Branch> findByTenant(Integer tenantId) {
		validateTenantExists(tenantId);
		return repository.findByTenantId(tenantId);
	}

	// US-15-04: sólo sucursales activas (endpoint público del client). El backoffice
	// sigue usando findByTenant/findAll para ver también las inactivas.
	public List<Branch> findActiveByTenant(Integer tenantId) {
		validateTenantExists(tenantId);
		return repository.findByTenantIdAndActiveTrue(tenantId);
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

	public Branch updateConfig(Integer id, Integer tenantId, Integer maxShiftDurationMinutes,
			String name, String address, String phone, String imageUrl, BigDecimal deliveryFee,
			BigDecimal serviceFee, Integer estimatedDeliveryMinutes) {
		if (!repository.existsByIdAndTenantId(id, tenantId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch does not belong to your tenant");
		}
		Branch branch = findById(id);
		branch.setMaxShiftDurationMinutes(maxShiftDurationMinutes);
		// US-15-02 / US-15-F-01: patch parcial — solo se actualizan los campos que
		// vienen en el body. Un null significa "omitido" y conserva el valor existente
		// (las columnas son NOT NULL, así que nunca deben pisarse con null).
		if (name != null) {
			branch.setName(name);
		}
		if (address != null) {
			branch.setAddress(address);
		}
		if (phone != null) {
			branch.setPhone(phone);
		}
		if (imageUrl != null) {
			branch.setImageUrl(imageUrl);
		}
		if (deliveryFee != null) {
			branch.setDeliveryFee(deliveryFee);
		}
		if (serviceFee != null) {
			branch.setServiceFee(serviceFee);
		}
		if (estimatedDeliveryMinutes != null) {
			branch.setEstimatedDeliveryMinutes(estimatedDeliveryMinutes);
		}
		repository.save(branch);
		// save() (merge) puede devolver el branch con el tenant como proxy lazy sin
		// inicializar; releemos con findById (que trae el tenant vía @EntityGraph)
		// para que BranchMapper.toResponseDTO no falle con LazyInitializationException.
		return findById(id);
	}

	// US-15-04: activa/desactiva una sucursal (ADMIN). Al desactivar valida que no
	// haya un turno abierto; reactivar no tiene esa restricción. No hay mínimo de
	// sucursales activas ni auto-gestión de StaffUser asociados.
	public void setStatus(Integer id, Integer tenantId, boolean active) {
		if (!repository.existsByIdAndTenantId(id, tenantId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch does not belong to your tenant");
		}
		if (!active && workShiftRepository.existsByBranchIdAndStatus(id, ShiftStatus.OPEN)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"No se puede desactivar una sucursal con un turno abierto");
		}
		Branch branch = findById(id);
		branch.setActive(active);
		repository.save(branch);
	}

	public BranchQR saveQrConfig(Integer branchId, String mpPosId, String mpQrId) {
		Branch branch = findById(branchId);
		BranchQR qr = branchQrRepository.findByBranchId(branchId)
			.orElseGet(() -> BranchQR.builder().branch(branch).build());
		qr.setMpPosId(mpPosId);
		qr.setMpQrId(mpQrId);
		qr.setActive(true);
		return branchQrRepository.save(qr);
	}

	// --- US-13-07: gestión de horarios por sucursal (ADMIN) ---

	public List<BranchSchedule> findScheduleByBranch(Integer branchId, Integer tenantId) {
		validateBranchOwnership(branchId, tenantId);
		return branchScheduleRepository.findByBranchId(branchId);
	}

	public List<BranchSchedule> upsertSchedule(Integer branchId, Integer tenantId, List<BranchSchedule> days) {
		validateBranchOwnership(branchId, tenantId);
		Branch branch = findById(branchId);

		for (BranchSchedule day : days) {
			validateScheduleDay(day);
			BranchSchedule entity = branchScheduleRepository
				.findByBranchIdAndDayOfWeek(branchId, day.getDayOfWeek())
				.orElseGet(() -> {
					BranchSchedule created = new BranchSchedule();
					created.setBranch(branch);
					created.setDayOfWeek(day.getDayOfWeek());
					return created;
				});
			entity.setActive(day.isActive());
			entity.setOpenTime(day.getOpenTime());
			entity.setCloseTime(day.getCloseTime());
			entity.setOpenTime2(day.getOpenTime2());
			entity.setCloseTime2(day.getCloseTime2());
			branchScheduleRepository.save(entity);
		}

		return branchScheduleRepository.findByBranchId(branchId);
	}

	private void validateBranchOwnership(Integer branchId, Integer tenantId) {
		if (!repository.existsByIdAndTenantId(branchId, tenantId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch does not belong to your tenant");
		}
	}

	private static void validateScheduleDay(BranchSchedule day) {
		if (day.getDayOfWeek() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayOfWeek is required");
		}
		if (day.isActive() && (day.getOpenTime() == null || day.getCloseTime() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"openTime and closeTime are required when active is true");
		}
		boolean hasOpen2 = day.getOpenTime2() != null;
		boolean hasClose2 = day.getCloseTime2() != null;
		if (hasOpen2 != hasClose2) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"openTime2 and closeTime2 must both be present or both absent");
		}
	}

	public boolean isOpen(Integer branchId) {
		return isOpenAt(branchId, LocalDate.now(), LocalTime.now());
	}

	/**
	 * US-13-06: determina si la sucursal está operativa en una fecha/hora.
	 * (1) Si existe un override para la fecha: si está inactivo, cerrado; si está
	 *     activo, se evalúan sus franjas. (2) Si no hay override, se usa el
	 *     branch_schedule del día de la semana: si no existe o está inactivo,
	 *     cerrado; si está activo, se evalúan sus franjas. Sin franjas definidas
	 *     y activo = abierto todo el día.
	 */
	boolean isOpenAt(Integer branchId, LocalDate date, LocalTime time) {
		Optional<BranchScheduleOverride> override =
			branchScheduleOverrideRepository.findByBranchIdAndDate(branchId, date);
		if (override.isPresent()) {
			BranchScheduleOverride o = override.get();
			if (!o.isActive()) {
				return false;
			}
			return withinFrames(time, o.getOpenTime(), o.getCloseTime(), o.getOpenTime2(), o.getCloseTime2());
		}

		WeekDay day = WeekDay.from(date.getDayOfWeek());
		Optional<BranchSchedule> schedule =
			branchScheduleRepository.findByBranchIdAndDayOfWeek(branchId, day);
		if (schedule.isEmpty()) {
			return false;
		}
		BranchSchedule s = schedule.get();
		if (!s.isActive()) {
			return false;
		}
		return withinFrames(time, s.getOpenTime(), s.getCloseTime(), s.getOpenTime2(), s.getCloseTime2());
	}

	/**
	 * Verdadero si {@code time} cae en la franja 1 o la franja 2. Si ninguna franja
	 * está definida (todos los límites null), el local se considera abierto todo el día.
	 */
	private static boolean withinFrames(LocalTime time, LocalTime open1, LocalTime close1,
			LocalTime open2, LocalTime close2) {
		boolean frame1Defined = open1 != null && close1 != null;
		boolean frame2Defined = open2 != null && close2 != null;
		if (!frame1Defined && !frame2Defined) {
			return true;
		}
		return (frame1Defined && withinFrame(time, open1, close1))
			|| (frame2Defined && withinFrame(time, open2, close2));
	}

	private static boolean withinFrame(LocalTime time, LocalTime open, LocalTime close) {
		return !time.isBefore(open) && !time.isAfter(close);
	}

	private Tenant validateTenantExists(Integer tenantId) {
		return tenantRepository.findById(tenantId)
			.orElseThrow(() -> new TenantNotFoundException(tenantId));
	}
}
