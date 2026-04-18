package com.laroka.backend.pizzeria.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.pizzeria.dto.PizzeriaRequestDTO;
import com.laroka.backend.pizzeria.dto.PizzeriaResponseDTO;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.mapper.PizzeriaMapper;
import com.laroka.backend.pizzeria.service.PizzeriaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pizzerias")
@RequiredArgsConstructor
public class PizzeriaController {

	private final PizzeriaService service;
	private final PizzeriaMapper mapper;

	@GetMapping("/{id}")
	public ResponseEntity<PizzeriaResponseDTO> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.findById(id)));
	}

	@GetMapping
	public ResponseEntity<List<PizzeriaResponseDTO>> findAll() {
		return ResponseEntity.ok(service.findAll().stream().map(mapper::toResponseDTO).toList());
	}

	@PostMapping
	public ResponseEntity<PizzeriaResponseDTO> create(@RequestBody PizzeriaRequestDTO dto) {
		Pizzeria saved = service.create(mapper.toEntity(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved));
	}

	@PutMapping("/{id}")
	public ResponseEntity<PizzeriaResponseDTO> update(@PathVariable Integer id, @RequestBody PizzeriaRequestDTO dto) {
		return ResponseEntity.ok(mapper.toResponseDTO(service.update(id, mapper.toEntity(dto))));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Integer id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
