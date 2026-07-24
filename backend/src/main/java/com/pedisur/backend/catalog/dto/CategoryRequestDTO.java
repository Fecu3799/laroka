package com.pedisur.backend.catalog.dto;

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

	@NotNull(message = "Tenant ID is required")
	private Integer tenantId;

	// US-CAT-03: tipo maestro obligatorio. El name se precarga con category_type.name en el
	// frontend al elegir el tipo, pero es editable — por eso ambos campos son de entrada.
	@NotNull(message = "Category type ID is required")
	private Integer categoryTypeId;
}
