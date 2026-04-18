package com.laroka.backend.catalog.dto;

import java.time.LocalDateTime;

import com.laroka.backend.pizzeria.dto.PizzeriaResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponseDTO {
	private Integer id;
	private String name;
	private Integer pizzeriaId;
	private PizzeriaResponseDTO pizzeria;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
