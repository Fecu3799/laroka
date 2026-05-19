package com.laroka.backend.staffuser.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.staffuser.dto.StaffUserRequestDTO;
import com.laroka.backend.staffuser.dto.StaffUserResponseDTO;
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

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Create staff user", description = "Creates a new staff user (ADMIN only)")
	public ResponseEntity<StaffUserResponseDTO> create(@Valid @RequestBody StaffUserRequestDTO dto) {
		var staffUser = staffUserMapper.toEntity(dto);
		var saved = staffUserService.create(staffUser);
		return ResponseEntity.status(HttpStatus.CREATED).body(staffUserMapper.toResponseDTO(saved));
	}
}
