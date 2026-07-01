package com.laroka.backend.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProductsAvailabilityResponseDTO {

	// Cantidad de BranchProduct efectivamente actualizados.
	private int updated;
}
