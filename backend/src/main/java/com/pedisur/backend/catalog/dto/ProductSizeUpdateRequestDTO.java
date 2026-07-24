package com.pedisur.backend.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * US-SIZE-04: edición de un tamaño. Los dos campos son opcionales — se modifica sólo lo que
 * viene. `active=false` es la baja del catálogo (soft-delete): la fila nunca se borra porque
 * order_item.product_size_id la referencia en los pedidos históricos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSizeUpdateRequestDTO {

	@DecimalMin(value = "0.01", message = "price must be greater than 0")
	private BigDecimal price;

	private Boolean active;
}
