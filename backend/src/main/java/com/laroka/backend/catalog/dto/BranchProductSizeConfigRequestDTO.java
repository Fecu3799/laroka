package com.laroka.backend.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * US-SIZE-04: override de precio de un tamaño en una sucursal. Mismo patrón que
 * BranchProductConfigRequestDTO, pero a nivel tamaño: branchId en el body, precio nullable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProductSizeConfigRequestDTO {

	@NotNull(message = "branchId is required")
	private Integer branchId;

	// Nullable: null limpia el override y el tamaño vuelve a su precio base.
	@DecimalMin(value = "0.01", message = "priceOverride must be greater than 0")
	private BigDecimal priceOverride;
}
