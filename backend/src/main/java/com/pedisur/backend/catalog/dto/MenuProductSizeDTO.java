package com.pedisur.backend.catalog.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * US-SIZE-F-02: un tamaño de producto tal como lo consume el client. `price` ya viene
 * resuelto para la sucursal (branch_product_size.price_override ?? product_size.price),
 * mismo criterio que el `price` del producto — el client nunca calcula precios.
 *
 * `id` es el productSizeId que el client manda de vuelta en el ítem del pedido.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuProductSizeDTO {
	private Integer id;
	private String size;
	private BigDecimal price;
}
