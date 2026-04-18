package com.laroka.backend.branch.dto;

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
public class BranchRequestDTO {
	@NotBlank(message = "Branch name is required")
	private String name;

	@NotBlank(message = "Branch address is required")
	private String address;

	@NotNull(message = "Pizzeria ID is required")
	private Integer pizzeriaId;
}
