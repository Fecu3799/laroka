package com.laroka.backend.catalog.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.laroka.backend.catalog.dto.AvailabilityUpdateDTO;
import com.laroka.backend.catalog.dto.ProductRequestDTO;
import com.laroka.backend.catalog.dto.ProductResponseDTO;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.mapper.ProductMapper;
import com.laroka.backend.catalog.service.ProductService;
import com.laroka.backend.shared.security.CustomUserDetails;
import com.laroka.backend.shared.security.SecurityUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/backoffice/products")
@RequiredArgsConstructor
@Tag(name = "Backoffice Products", description = "Manage products in the catalog")
public class ProductController {

	private final ProductService service;
	private final ProductMapper mapper;
	private final SecurityUtils securityUtils;

	@GetMapping("/{id}")
	@Operation(summary = "Get product by ID", description = "Returns a specific product")
	public ResponseEntity<ProductResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	@Operation(summary = "List products", description = "Returns all products, optionally filtered by category or tenant")
	public ResponseEntity<List<ProductResponseDTO>> findAll(
			@RequestParam(required = false) Integer categoryId,
			@RequestParam(required = false) Integer tenantId) {
		List<Product> products;
		if (categoryId != null) {
			products = service.findByCategory(categoryId);
		} else if (tenantId != null) {
			products = service.findByTenant(tenantId);
		} else {
			products = service.findAll();
		}
		return ResponseEntity.ok(products.stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	@Operation(summary = "Create product", description = "Creates a new product")
	public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody ProductRequestDTO dto) {
		Product saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update product", description = "Updates an existing product")
	public ResponseEntity<ProductResponseDTO> update(@PathVariable Integer id, @Valid @RequestBody ProductRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete product", description = "Deletes a product")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{id}/availability")
	@Operation(summary = "Update product availability", description = "Updates the availability status of a product")
	public ResponseEntity<ProductResponseDTO> updateAvailability(
			@PathVariable Integer id,
			@Valid @RequestBody AvailabilityUpdateDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal,
			HttpServletRequest request) {
		Integer userBranchId = securityUtils.resolveBranchId(principal, request);
		return ResponseEntity.ok(mapper.toResponseDTO(service.updateAvailability(id, dto.getAvailable(), userBranchId)));
	}
}
