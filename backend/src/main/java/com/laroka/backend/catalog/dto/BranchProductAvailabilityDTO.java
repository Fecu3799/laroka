package com.laroka.backend.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProductAvailabilityDTO {
	private Integer productId;
	private String name;
	private Integer categoryId;
	private String categoryName;
	// Nullable: el front muestra un placeholder si no hay imagen.
	private String imageUrl;
	// Disponibilidad del producto para esta sucursal (BranchProduct.available).
	private Boolean available;
}
