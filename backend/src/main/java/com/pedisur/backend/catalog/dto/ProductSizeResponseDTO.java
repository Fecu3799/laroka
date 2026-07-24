package com.pedisur.backend.catalog.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * US-SIZE-04: tamaño de un producto tal como lo consume el backoffice. Incluye `active`, a
 * diferencia del menú del client (MenuProductSizeDTO), que sólo expone los activos y con el
 * precio ya resuelto por sucursal. Acá el precio es el base a nivel tenant.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSizeResponseDTO {
	private Integer id;
	private Integer productId;
	private String size;
	private BigDecimal price;
	private boolean active;
}
