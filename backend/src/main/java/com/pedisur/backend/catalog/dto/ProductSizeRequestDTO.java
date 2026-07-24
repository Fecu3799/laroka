package com.pedisur.backend.catalog.dto;

import java.math.BigDecimal;

import com.pedisur.backend.catalog.entity.ProductSizeName;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * US-SIZE-04: alta de un tamaño. `size` viaja explícito aunque hoy el único valor aceptado
 * sea CHICA — el service rechaza el resto con 422, y dejarlo explícito evita tener que
 * cambiar el contrato si algún día se agrega otro tamaño alternativo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSizeRequestDTO {

	@NotNull(message = "size is required")
	private ProductSizeName size;

	@NotNull(message = "price is required")
	@DecimalMin(value = "0.01", message = "price must be greater than 0")
	private BigDecimal price;
}
