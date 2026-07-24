package com.pedisur.backend.catalog.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProductsAvailabilityRequestDTO {

	// Puede venir vacía (no falla, actualiza 0). Null sí es inválido.
	@NotNull(message = "productIds is required")
	private List<Integer> productIds;

	@NotNull(message = "available is required")
	private Boolean available;
}
