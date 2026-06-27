package com.laroka.backend.catalog.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProductConfigDTO {
	private Integer branchId;
	private String branchName;
	private Boolean available;
	// Nullable: null significa que la sucursal usa el precio base del producto.
	private BigDecimal priceOverride;
	// Precio resuelto: priceOverride si existe, si no el precio base del producto.
	private BigDecimal effectivePrice;
}
