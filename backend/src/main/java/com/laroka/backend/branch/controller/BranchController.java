package com.laroka.backend.branch.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.laroka.backend.branch.dto.AcceptingOrdersResponseDTO;
import com.laroka.backend.branch.dto.BranchConfigRequestDTO;
import com.laroka.backend.branch.dto.BranchRequestDTO;
import com.laroka.backend.branch.dto.BranchResponseDTO;
import com.laroka.backend.branch.dto.BranchScheduleDayRequestDTO;
import com.laroka.backend.branch.dto.BranchScheduleDayResponseDTO;
import com.laroka.backend.branch.dto.BranchStatusRequestDTO;
import com.laroka.backend.catalog.dto.BranchProductAvailabilityDTO;
import com.laroka.backend.catalog.dto.BranchProductsAvailabilityRequestDTO;
import com.laroka.backend.catalog.dto.BranchProductsAvailabilityResponseDTO;
import com.laroka.backend.catalog.mapper.BranchProductAvailabilityMapper;
import com.laroka.backend.catalog.service.ProductService;
import com.laroka.backend.branch.dto.QrConfigRequestDTO;
import com.laroka.backend.branch.dto.QrConfigResponseDTO;
import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.entity.BranchQR;
import com.laroka.backend.branch.entity.BranchSchedule;
import com.laroka.backend.branch.mapper.BranchMapper;
import com.laroka.backend.branch.mapper.BranchScheduleMapper;
import com.laroka.backend.branch.service.BranchService;
import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.shared.security.SecurityUtils;
import com.laroka.backend.shift.service.WorkShiftService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/branches")
@RequiredArgsConstructor
@Tag(name = "Backoffice Branches", description = "Manage branches for backoffice users")
public class BranchController {

	private final BranchService service;
	private final BranchMapper mapper;
	private final BranchScheduleMapper scheduleMapper;
	private final WorkShiftService workShiftService;
	private final SecurityUtils securityUtils;
	private final ProductService productService;
	private final BranchProductAvailabilityMapper branchProductAvailabilityMapper;

	@GetMapping("/{id}")
	@Operation(summary = "Get branch by ID", description = "Returns a specific branch")
	public ResponseEntity<BranchResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	@Operation(summary = "List branches", description = "Returns all branches, optionally filtered by tenant")
	public ResponseEntity<List<BranchResponseDTO>> findAll(@RequestParam(required = false) Integer tenantId) {
		List<Branch> branches = tenantId != null
			? service.findByTenant(tenantId)
			: service.findAll();
		return ResponseEntity.ok(branches.stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	@Operation(summary = "Create branch", description = "Creates a new branch")
	public ResponseEntity<BranchResponseDTO> create(@Valid @RequestBody BranchRequestDTO dto) {
		Branch saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update branch", description = "Updates an existing branch")
	public ResponseEntity<BranchResponseDTO> update(@PathVariable Integer id, @Valid @RequestBody BranchRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete branch", description = "Deletes a branch")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/toggle-orders")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "Toggle accepting orders",
			description = "Flips the acceptingOrders flag for the active branch. Requires an open shift; "
					+ "returns 422 if no active shift exists for the branch.")
	public ResponseEntity<AcceptingOrdersResponseDTO> toggleOrders(
			@AuthenticationPrincipal CustomUserDetails principal,
			HttpServletRequest request) {
		Integer branchId = securityUtils.resolveBranchId(principal, request);
		boolean accepting = workShiftService.toggleAcceptingOrders(branchId);
		return ResponseEntity.ok(AcceptingOrdersResponseDTO.builder().acceptingOrders(accepting).build());
	}

	@PatchMapping("/{id}/config")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Update branch config", description = "Updates configurable settings for a branch (ADMIN only)")
	public ResponseEntity<BranchResponseDTO> updateConfig(
			@PathVariable Integer id,
			@Valid @RequestBody BranchConfigRequestDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal) {
		Branch updated = service.updateConfig(id, principal.getTenantId(), dto.getMaxShiftDurationMinutes(),
				dto.getName(), dto.getAddress(), dto.getPhone(), dto.getImageUrl(), dto.getDeliveryFee(),
				dto.getServiceFee(), dto.getEstimatedDeliveryMinutes());
		return ResponseEntity.ok(mapper.toResponseDTO(updated));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Set branch status",
			description = "Activates or deactivates a branch (ADMIN only). Deactivating a branch with an open "
					+ "work shift returns 400. Inactive branches are excluded from the public GET /branches.")
	public ResponseEntity<Void> setStatus(
			@PathVariable Integer id,
			@Valid @RequestBody BranchStatusRequestDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal) {
		service.setStatus(id, principal.getTenantId(), dto.getActive());
		return ResponseEntity.ok().build();
	}

	@GetMapping("/{id}/products")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "List products with availability for a branch",
			description = "Returns every product with its availability for the branch (available and unavailable), "
					+ "including inactive branches. MANAGER is scoped to its own branch; ADMIN to any branch of its "
					+ "tenant (403 otherwise).")
	public ResponseEntity<List<BranchProductAvailabilityDTO>> getBranchProducts(
			@PathVariable Integer id,
			@AuthenticationPrincipal CustomUserDetails principal) {
		securityUtils.validateBranchScope(principal, id);
		return ResponseEntity.ok(branchProductAvailabilityMapper.toList(productService.getBranchProducts(id)));
	}

	@PatchMapping("/{id}/products/availability")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "Bulk update product availability for a branch",
			description = "Sets `available` for every BranchProduct of the branch whose productId is in the list, "
					+ "in a single transaction. productIds without a BranchProduct for the branch are ignored. "
					+ "A deactivated branch is rejected with 422. Returns the number of updated records.")
	public ResponseEntity<BranchProductsAvailabilityResponseDTO> updateProductsAvailability(
			@PathVariable Integer id,
			@Valid @RequestBody BranchProductsAvailabilityRequestDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal) {
		// MANAGER solo puede operar sobre su propia sucursal; ADMIN sobre cualquiera de su
		// tenant. Sin esto, un MANAGER de la sucursal A podría modificar la B por el path.
		securityUtils.validateBranchScope(principal, id);
		int updated = productService.updateBranchProductsAvailability(id, dto.getProductIds(), dto.getAvailable());
		return ResponseEntity.ok(BranchProductsAvailabilityResponseDTO.builder().updated(updated).build());
	}

	@GetMapping("/{id}/schedule")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Get branch weekly schedule",
			description = "Returns exactly 7 entries (MON..SUN). Days without a record are returned with active=false "
					+ "and null hours. ADMIN only; the branch must belong to the ADMIN's tenant.")
	public ResponseEntity<List<BranchScheduleDayResponseDTO>> getSchedule(
			@PathVariable Integer id,
			@AuthenticationPrincipal CustomUserDetails principal) {
		List<BranchSchedule> schedule = service.findScheduleByBranch(id, principal.getTenantId());
		return ResponseEntity.ok(scheduleMapper.toWeekResponse(schedule));
	}

	@PutMapping("/{id}/schedule")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Upsert branch weekly schedule",
			description = "Receives the list of days and upserts the branch schedule. ADMIN only; the branch must "
					+ "belong to the ADMIN's tenant. Returns 400 on incomplete time frames.")
	public ResponseEntity<List<BranchScheduleDayResponseDTO>> upsertSchedule(
			@PathVariable Integer id,
			@RequestBody List<BranchScheduleDayRequestDTO> days,
			@AuthenticationPrincipal CustomUserDetails principal) {
		List<BranchSchedule> data = days.stream().map(scheduleMapper::toEntity).toList();
		List<BranchSchedule> saved = service.upsertSchedule(id, principal.getTenantId(), data);
		return ResponseEntity.ok(scheduleMapper.toWeekResponse(saved));
	}

	@PostMapping("/{id}/qr-config")
	@Operation(summary = "Save QR config", description = "Creates or updates the MercadoPago QR configuration for a branch.")
	public ResponseEntity<QrConfigResponseDTO> saveQrConfig(
			@PathVariable Integer id,
			@Valid @RequestBody QrConfigRequestDTO dto) {
		BranchQR saved = service.saveQrConfig(id, dto.getMpPosId(), dto.getMpQrId());
		return ResponseEntity.ok(toQrConfigResponse(saved));
	}

	private QrConfigResponseDTO toQrConfigResponse(BranchQR qr) {
		return QrConfigResponseDTO.builder()
				.id(qr.getId())
				.branchId(qr.getBranch().getId())
				.mpPosId(qr.getMpPosId())
				.mpQrId(qr.getMpQrId())
				.active(qr.isActive())
				.build();
	}
}
