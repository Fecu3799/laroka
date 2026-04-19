package com.laroka.backend.catalog.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.laroka.backend.catalog.dto.CategoryRequestDTO;
import com.laroka.backend.catalog.dto.CategoryResponseDTO;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.mapper.CategoryMapper;
import com.laroka.backend.catalog.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/categories")
@RequiredArgsConstructor
@Tag(name = "Backoffice Categories", description = "Manage product categories")
public class CategoryController {

	private final CategoryService service;
	private final CategoryMapper mapper;

	@GetMapping("/{id}")
	@Operation(summary = "Get category by ID", description = "Returns a specific category")
	public ResponseEntity<CategoryResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	@Operation(summary = "List categories", description = "Returns all categories, optionally filtered by pizzeria")
	public ResponseEntity<List<CategoryResponseDTO>> findAll(@RequestParam(required = false) Integer pizzeriaId) {
		List<Category> categories = pizzeriaId != null
			? service.findByPizzeria(pizzeriaId)
			: service.findAll();
		return ResponseEntity.ok(categories.stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	@Operation(summary = "Create category", description = "Creates a new category")
	public ResponseEntity<CategoryResponseDTO> create(@Valid @RequestBody CategoryRequestDTO dto) {
		Category saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update category", description = "Updates an existing category")
	public ResponseEntity<CategoryResponseDTO> update(@PathVariable Integer id, @Valid @RequestBody CategoryRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete category", description = "Deletes a category")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
