package com.pedisur.backend.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProductConfigRequestDTO {

	@NotNull(message = "branchId is required")
	private Integer branchId;

	// Nullable: null limpia el override y el producto vuelve al precio base.
	@DecimalMin(value = "0.01", message = "priceOverride must be greater than 0")
	private BigDecimal priceOverride;

	// Nullable: si viene null, la disponibilidad no se modifica.
	private Boolean available;
}
