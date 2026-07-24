package com.pedisur.backend.catalog.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import com.pedisur.backend.catalog.dto.AvailabilityUpdateDTO;
import com.pedisur.backend.catalog.dto.BranchProductConfigDTO;
import com.pedisur.backend.catalog.dto.BranchProductConfigRequestDTO;
import com.pedisur.backend.catalog.dto.BranchProductSizeConfigRequestDTO;
import com.pedisur.backend.catalog.dto.ProductPriceUpdateRequestDTO;
import com.pedisur.backend.catalog.dto.ProductRequestDTO;
import com.pedisur.backend.catalog.dto.ProductResponseDTO;
import com.pedisur.backend.catalog.dto.ProductSizeRequestDTO;
import com.pedisur.backend.catalog.dto.ProductSizeResponseDTO;
import com.pedisur.backend.catalog.dto.ProductSizeUpdateRequestDTO;
import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.catalog.entity.ProductSize;
import com.pedisur.backend.catalog.mapper.BranchProductConfigMapper;
import com.pedisur.backend.catalog.mapper.ProductMapper;
import com.pedisur.backend.catalog.mapper.ProductSizeMapper;
import com.pedisur.backend.catalog.service.ProductService;
import com.pedisur.backend.catalog.service.ProductSizeService;
import com.pedisur.backend.shared.security.CustomUserDetails;
import com.pedisur.backend.shared.security.SecurityUtils;
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
	private final ProductSizeService productSizeService;
	private final ProductMapper mapper;
	private final BranchProductConfigMapper branchProductConfigMapper;
	private final ProductSizeMapper productSizeMapper;
	private final SecurityUtils securityUtils;

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "Get product by ID", description = "Returns a specific product")
	public ResponseEntity<ProductResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
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
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Create product", description = "Creates a new product")
	public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody ProductRequestDTO dto) {
		Product saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Update product", description = "Updates an existing product")
	public ResponseEntity<ProductResponseDTO> update(@PathVariable Integer id, @Valid @RequestBody ProductRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Delete product", description = "Deletes a product")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{id}/availability")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "Update product availability", description = "Updates the availability status of a product")
	public ResponseEntity<ProductResponseDTO> updateAvailability(
			@PathVariable Integer id,
			@Valid @RequestBody AvailabilityUpdateDTO dto,
			@AuthenticationPrincipal CustomUserDetails principal,
			HttpServletRequest request) {
		Integer userBranchId = securityUtils.resolveBranchId(principal, request);
		return ResponseEntity.ok(mapper.toResponseDTO(service.updateAvailability(id, dto.getAvailable(), userBranchId)));
	}

	@GetMapping("/{id}/branch-config")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "Get product config per branch",
			description = "Returns one entry per branch of the tenant with availability, price override "
					+ "(nullable) and the effective price (override if present, base price otherwise).")
	public ResponseEntity<List<BranchProductConfigDTO>> getBranchConfig(@PathVariable Integer id) {
		return ResponseEntity.ok(branchProductConfigMapper.toConfigList(service.getBranchProductConfig(id)));
	}

	@PatchMapping("/{id}/branch-config")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "Update product config for a branch",
			description = "Updates the price override and/or availability of a product for a specific branch. "
					+ "A null priceOverride clears the override and the product reverts to its base price.")
	public ResponseEntity<ProductResponseDTO> updateBranchConfig(
			@PathVariable Integer id,
			@Valid @RequestBody BranchProductConfigRequestDTO dto) {
		Product updated = service.updateBranchConfig(id, dto.getBranchId(), dto.getPriceOverride(), dto.getAvailable());
		return ResponseEntity.ok(mapper.toResponseDTO(updated));
	}

	@PutMapping("/{id}/price")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Update product base price",
			description = "Updates the base price of a product. If applyToAllBranches is true, also clears the "
					+ "price override of every branch product, leveling the price across all branches.")
	public ResponseEntity<ProductResponseDTO> updatePrice(
			@PathVariable Integer id,
			@Valid @RequestBody ProductPriceUpdateRequestDTO dto) {
		Product updated = service.updatePrice(id, dto.getPrice(), dto.getApplyToAllBranches());
		return ResponseEntity.ok(mapper.toResponseDTO(updated));
	}

	// ── Tamaños (US-SIZE-04) ────────────────────────────────────────────────────

	@GetMapping("/{id}/sizes")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "List product sizes",
			description = "Returns every size of the product, active and inactive. The client menu only "
					+ "exposes the active ones.")
	public ResponseEntity<List<ProductSizeResponseDTO>> getSizes(@PathVariable Integer id) {
		return ResponseEntity.ok(productSizeMapper.toResponseDTOList(productSizeService.findByProduct(id)));
	}

	@PostMapping("/{id}/sizes")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Create a product size",
			description = "Creates a size row with its base price. Only CHICA is accepted: the large size is "
					+ "implicit and its price is always the product base price, so a GRANDE row would create a "
					+ "second source of truth. Returns 422 for any other size, for a duplicate size, or when the "
					+ "product category does not allow sizes.")
	public ResponseEntity<ProductSizeResponseDTO> createSize(
			@PathVariable Integer id,
			@Valid @RequestBody ProductSizeRequestDTO dto) {
		ProductSize created = productSizeService.create(id, dto.getSize(), dto.getPrice());
		return ResponseEntity.status(HttpStatus.CREATED).body(productSizeMapper.toResponseDTO(created));
	}

	@PatchMapping("/{id}/sizes/{sizeId}")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Update a product size",
			description = "Updates the base price and/or the active flag of a size. Both fields are optional. "
					+ "active=false is a soft delete: the row is kept because historical order items reference it.")
	public ResponseEntity<ProductSizeResponseDTO> updateSize(
			@PathVariable Integer id,
			@PathVariable Integer sizeId,
			@Valid @RequestBody ProductSizeUpdateRequestDTO dto) {
		ProductSize updated = productSizeService.update(id, sizeId, dto.getPrice(), dto.getActive());
		return ResponseEntity.ok(productSizeMapper.toResponseDTO(updated));
	}

	@PatchMapping("/{id}/sizes/{sizeId}/branch-config")
	@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
	@Operation(summary = "Update size price override for a branch",
			description = "Sets the price override of a size for a specific branch. A null priceOverride clears "
					+ "the override and the size reverts to its base price.")
	public ResponseEntity<Void> updateSizeBranchConfig(
			@PathVariable Integer id,
			@PathVariable Integer sizeId,
			@Valid @RequestBody BranchProductSizeConfigRequestDTO dto) {
		productSizeService.updateBranchOverride(dto.getBranchId(), id, sizeId, dto.getPriceOverride());
		return ResponseEntity.noContent().build();
	}
}
