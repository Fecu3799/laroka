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

import com.laroka.backend.catalog.dto.ProductRequestDTO;
import com.laroka.backend.catalog.dto.ProductResponseDTO;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.mapper.ProductMapper;
import com.laroka.backend.catalog.service.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService service;
	private final ProductMapper mapper;

	@GetMapping("/{id}")
	public ResponseEntity<ProductResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<List<ProductResponseDTO>> findAll(
			@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) Integer categoryId,
			@RequestParam(required = false) Integer pizzeriaId) {
		List<Product> products;
		if (branchId != null) {
			products = service.findByBranch(branchId);
		} else if (categoryId != null) {
			products = service.findByCategory(categoryId);
		} else if (pizzeriaId != null) {
			products = service.findByPizzeria(pizzeriaId);
		} else {
			products = service.findAll();
		}
		return ResponseEntity.ok(products.stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	public ResponseEntity<ProductResponseDTO> create(@RequestBody ProductRequestDTO dto) {
		Product saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ProductResponseDTO> update(@PathVariable Integer id, @RequestBody ProductRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
