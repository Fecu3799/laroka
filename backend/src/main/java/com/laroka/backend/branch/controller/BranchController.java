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
import com.laroka.backend.branch.dto.BranchRequestDTO;
import com.laroka.backend.branch.dto.BranchResponseDTO;
import com.laroka.backend.branch.dto.QrConfigRequestDTO;
import com.laroka.backend.branch.dto.QrConfigResponseDTO;
import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.entity.BranchQR;
import com.laroka.backend.branch.mapper.BranchMapper;
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
	private final WorkShiftService workShiftService;
	private final SecurityUtils securityUtils;

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
