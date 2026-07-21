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

	// US-SIZE-F-01: mismos dos campos, pero para el tamaño activo del producto. Los tres
	// vienen en null cuando el producto no tiene tamaño cargado (todo lo que no es pizza).
	private Integer productSizeId;
	private BigDecimal sizePriceOverride;
	private BigDecimal sizeEffectivePrice;
}
