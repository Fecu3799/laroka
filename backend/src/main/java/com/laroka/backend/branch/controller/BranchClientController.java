package com.laroka.backend.branch.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.branch.dto.BranchPublicDTO;
import com.laroka.backend.branch.dto.BranchStatusDTO;
import com.laroka.backend.branch.mapper.BranchMapper;
import com.laroka.backend.branch.service.BranchService;
import com.laroka.backend.catalog.dto.MenuCategoryDTO;
import com.laroka.backend.catalog.mapper.MenuMapper;
import com.laroka.backend.catalog.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
@Tag(name = "Branches", description = "Public API for branch information and menus")
public class BranchClientController {

	private final BranchService branchService;
	private final BranchMapper branchMapper;
	private final ProductService productService;
	private final MenuMapper menuMapper;

	@GetMapping
	@Operation(summary = "Get branches by tenant", description = "Returns the branches that belong to the given tenant. The tenantId query param is required; omitting it returns 400.")
	public ResponseEntity<List<BranchPublicDTO>> findAll(@RequestParam Integer tenantId) {
		return ResponseEntity.ok(branchService.findByTenant(tenantId).stream().map(branchMapper::toPublicDTO).toList());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get branch by ID", description = "Returns branch information including delivery and service fees")
	public ResponseEntity<BranchPublicDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(branchMapper.toPublicDTO(branchService.findById(id)));
	}

	@GetMapping("/{id}/status")
	@Operation(summary = "Get branch open/closed status", description = "Returns whether the branch is currently open based on its schedule (opening_time, closing_time, open_days).")
	public ResponseEntity<BranchStatusDTO> getBranchStatus(@PathVariable Integer id) {
		return ResponseEntity.ok(BranchStatusDTO.builder().open(branchService.isOpen(id)).build());
	}

	@GetMapping("/{id}/menu")
	@Operation(summary = "Get branch menu", description = "Returns menu for a specific branch with available products grouped by category")
	public ResponseEntity<List<MenuCategoryDTO>> getMenu(@PathVariable Integer id) {
		return ResponseEntity.ok(menuMapper.toMenu(productService.getMenuForBranch(id)));
	}
}