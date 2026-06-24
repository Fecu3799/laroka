package com.laroka.backend.staffuser.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.staffuser.dto.StaffUserPasswordResetRequestDTO;
import com.laroka.backend.staffuser.dto.StaffUserRequestDTO;
import com.laroka.backend.staffuser.dto.StaffUserResponseDTO;
import com.laroka.backend.staffuser.dto.StaffUserUpdateRequestDTO;
import com.laroka.backend.staffuser.mapper.StaffUserMapper;
import com.laroka.backend.staffuser.service.StaffUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/backoffice/staff-users")
@RequiredArgsConstructor
@Tag(name = "Backoffice Staff Users", description = "Manage staff users")
public class StaffUserController {

	private final StaffUserService staffUserService;
	private final StaffUserMapper staffUserMapper;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "List staff users", description = "Returns all staff users for the authenticated ADMIN's tenant")
	public ResponseEntity<List<StaffUserResponseDTO>> findAll(@AuthenticationPrincipal CustomUserDetails principal) {
		List<StaffUserResponseDTO> response = staffUserService.findAllByTenantId(principal.getTenantId())
			.stream()
			.map(staffUserMapper::toResponseDTO)
			.toList();
		return ResponseEntity.ok(response);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Create staff user", description = "Creates a new staff user (ADMIN only)")
	public ResponseEntity<StaffUserResponseDTO> create(@Valid @RequestBody StaffUserRequestDTO dto) {
		var staffUser = staffUserMapper.toEntity(dto);
		var saved = staffUserService.create(staffUser);
		return ResponseEntity.status(HttpStatus.CREATED).body(staffUserMapper.toResponseDTO(saved));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Update staff user", description = "Updates name, role and branch of a staff user (ADMIN only)")
	public ResponseEntity<StaffUserResponseDTO> update(
			@PathVariable Integer id,
			@Valid @RequestBody StaffUserUpdateRequestDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal) {
		var patch = staffUserMapper.toUpdateEntity(dto);
		var updated = staffUserService.update(id, principal.getTenantId(), patch);
		return ResponseEntity.ok(staffUserMapper.toResponseDTO(updated));
	}

	@PatchMapping("/{id}/password")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Reset staff user password", description = "Replaces the password of a staff user (ADMIN only)")
	public ResponseEntity<Void> resetPassword(
			@PathVariable Integer id,
			@Valid @RequestBody StaffUserPasswordResetRequestDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal) {
		staffUserService.resetPassword(id, principal.getTenantId(), dto.getNewPassword());
		return ResponseEntity.ok().build();
	}
}
