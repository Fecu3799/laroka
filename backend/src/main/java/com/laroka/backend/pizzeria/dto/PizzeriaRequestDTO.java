package com.laroka.backend.pizzeria.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PizzeriaRequestDTO {
	@NotBlank(message = "Pizzeria name is required")
	private String name;
}
