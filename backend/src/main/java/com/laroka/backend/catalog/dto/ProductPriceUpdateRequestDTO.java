package com.laroka.backend.catalog.dto;

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
public class ProductPriceUpdateRequestDTO {

	@NotNull(message = "price is required")
	@DecimalMin(value = "0.01", message = "price must be greater than 0")
	private BigDecimal price;

	@NotNull(message = "applyToAllBranches is required")
	private Boolean applyToAllBranches;
}
