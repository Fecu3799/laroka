package com.laroka.backend.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequestDTO {
	@NotBlank(message = "Category name is required")
	private String name;

	@NotNull(message = "Pizzeria ID is required")
	private Integer pizzeriaId;
}
