package com.laroka.backend.branch.controller;

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

import com.laroka.backend.branch.dto.BranchRequestDTO;
import com.laroka.backend.branch.dto.BranchResponseDTO;
import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.mapper.BranchMapper;
import com.laroka.backend.branch.service.BranchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backoffice/branches")
@RequiredArgsConstructor
public class BranchController {

	private final BranchService service;
	private final BranchMapper mapper;

	@GetMapping("/{id}")
	public ResponseEntity<BranchResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<List<BranchResponseDTO>> findAll(@RequestParam(required = false) Integer pizzeriaId) {
		List<Branch> branches = pizzeriaId != null
			? service.findByPizzeria(pizzeriaId)
			: service.findAll();
		return ResponseEntity.ok(branches.stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	public ResponseEntity<BranchResponseDTO> create(@RequestBody BranchRequestDTO dto) {
		Branch saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BranchResponseDTO> update(@PathVariable Integer id, @RequestBody BranchRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
