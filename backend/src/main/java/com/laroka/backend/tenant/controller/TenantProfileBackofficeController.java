package com.laroka.backend.tenant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.tenant.dto.TenantProfileRequestDTO;
import com.laroka.backend.tenant.dto.TenantProfileResponseDTO;
import com.laroka.backend.tenant.entity.TenantProfile;
import com.laroka.backend.tenant.mapper.TenantProfileMapper;
import com.laroka.backend.tenant.service.TenantProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/tenant/profile")
@RequiredArgsConstructor
@Tag(name = "Backoffice Tenant Profile", description = "Manage the authenticated tenant's business profile (ADMIN only)")
public class TenantProfileBackofficeController {

	private final TenantProfileService service;
	private final TenantProfileMapper mapper;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Get tenant profile", description = "Returns the profile of the authenticated ADMIN's tenant. Returns 404 if it does not exist yet.")
	public ResponseEntity<TenantProfileResponseDTO> getProfile(@AuthenticationPrincipal CustomUserDetails principal) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findByTenantId(principal.getTenantId())));
	}

	@PutMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Upsert tenant profile", description = "Creates or updates the profile of the authenticated ADMIN's tenant.")
	public ResponseEntity<TenantProfileResponseDTO> upsertProfile(
			@Valid @RequestBody TenantProfileRequestDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal) {
		TenantProfile saved = service.upsert(principal.getTenantId(), mapper.toEntity(dto));
		return ResponseEntity.ok(mapper.toResponseDTO(saved));
	}
}
