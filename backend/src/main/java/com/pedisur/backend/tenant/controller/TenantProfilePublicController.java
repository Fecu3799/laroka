package com.pedisur.backend.tenant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedisur.backend.tenant.dto.TenantProfilePublicDTO;
import com.pedisur.backend.tenant.mapper.TenantProfileMapper;
import com.pedisur.backend.tenant.service.TenantProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Profile", description = "Public API for tenant business profile")
public class TenantProfilePublicController {

	private final TenantProfileService service;
	private final TenantProfileMapper mapper;

	@GetMapping("/{tenantId}/profile")
	@Operation(summary = "Get public tenant profile", description = "Returns the public business profile for a tenant. Returns 404 if the tenant has no profile.")
	public ResponseEntity<TenantProfilePublicDTO> getProfile(@PathVariable Integer tenantId) {
		return ResponseEntity.ok(mapper.toPublicDTO(service.findByTenantId(tenantId)));
	}
}
