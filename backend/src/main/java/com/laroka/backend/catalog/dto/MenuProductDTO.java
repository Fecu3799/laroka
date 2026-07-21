package com.laroka.backend.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuProductDTO {
	private Integer id;
	private String name;
	private String description;
	private BigDecimal price;
	private String imageUrl;
	private Boolean available;
	// US-SIZE-F-02: tamaños activos con el precio efectivo de esta sucursal ya resuelto.
	// Lista vacía cuando el producto no tiene tamaños cargados (comportamiento previo).
	private List<MenuProductSizeDTO> sizes;
}
