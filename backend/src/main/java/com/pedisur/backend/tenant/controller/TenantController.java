package com.pedisur.backend.tenant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.pedisur.backend.tenant.dto.TenantRequestDTO;
import com.pedisur.backend.tenant.dto.TenantResponseDTO;
import com.pedisur.backend.tenant.entity.Tenant;
import com.pedisur.backend.tenant.mapper.TenantMapper;
import com.pedisur.backend.tenant.service.TenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Manage tenant information")
public class TenantController {

	private final TenantService service;
	private final TenantMapper mapper;

	@GetMapping("/{id}")
	@Operation(summary = "Get tenant by ID", description = "Returns a specific tenant")
	public ResponseEntity<TenantResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	@Operation(summary = "List all tenants", description = "Returns all tenants")
	public ResponseEntity<List<TenantResponseDTO>> findAll() {
		return ResponseEntity.ok(service.findAll().stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	@Operation(summary = "Create tenant", description = "Creates a new tenant")
	public ResponseEntity<TenantResponseDTO> create(@Valid @RequestBody TenantRequestDTO dto) {
		Tenant saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update tenant", description = "Updates an existing tenant")
	public ResponseEntity<TenantResponseDTO> update(@PathVariable Integer id, @Valid @RequestBody TenantRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete tenant", description = "Deletes a tenant")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
