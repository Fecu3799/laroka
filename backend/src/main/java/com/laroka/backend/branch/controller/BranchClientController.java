package com.laroka.backend.branch.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.branch.dto.BranchPublicDTO;
import com.laroka.backend.branch.mapper.BranchMapper;
import com.laroka.backend.branch.service.BranchService;
import com.laroka.backend.catalog.dto.MenuCategoryDTO;
import com.laroka.backend.catalog.mapper.MenuMapper;
import com.laroka.backend.catalog.service.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchClientController {

	private final BranchService branchService;
	private final BranchMapper branchMapper;
	private final ProductService productService;
	private final MenuMapper menuMapper;

	@GetMapping
	public ResponseEntity<List<BranchPublicDTO>> findAll() {
		return ResponseEntity.ok(branchService.findAll().stream().map(branchMapper::toPublicDTO).toList());
	}

	@GetMapping("/{id}/menu")
	public ResponseEntity<List<MenuCategoryDTO>> getMenu(@PathVariable Integer id) {
		return ResponseEntity.ok(menuMapper.toMenu(productService.findAvailableByBranch(id)));
	}
}
