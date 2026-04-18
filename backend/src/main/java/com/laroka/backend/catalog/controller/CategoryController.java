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

import com.laroka.backend.catalog.dto.CategoryRequestDTO;
import com.laroka.backend.catalog.dto.CategoryResponseDTO;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.mapper.CategoryMapper;
import com.laroka.backend.catalog.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService service;
	private final CategoryMapper mapper;

	@GetMapping("/{id}")
	public ResponseEntity<CategoryResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<List<CategoryResponseDTO>> findAll(@RequestParam(required = false) Integer pizzeriaId) {
		List<Category> categories = pizzeriaId != null
			? service.findByPizzeria(pizzeriaId)
			: service.findAll();
		return ResponseEntity.ok(categories.stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	public ResponseEntity<CategoryResponseDTO> create(@RequestBody CategoryRequestDTO dto) {
		Category saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	public ResponseEntity<CategoryResponseDTO> update(@PathVariable Integer id, @RequestBody CategoryRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
