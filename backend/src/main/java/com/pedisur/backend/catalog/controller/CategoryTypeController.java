package com.pedisur.backend.catalog.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedisur.backend.catalog.dto.CategoryTypeResponseDTO;
import com.pedisur.backend.catalog.mapper.CategoryTypeMapper;
import com.pedisur.backend.catalog.service.CategoryTypeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/category-types")
@RequiredArgsConstructor
@Tag(name = "Backoffice Category Types", description = "Master catalog of category types")
public class CategoryTypeController {

	private final CategoryTypeService service;
	private final CategoryTypeMapper mapper;

	@GetMapping
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "List active category types", description = "Returns the active master category types")
	public ResponseEntity<List<CategoryTypeResponseDTO>> findActive() {
		return ResponseEntity.ok(mapper.toResponseDTOList(service.findActive()));
	}
}
