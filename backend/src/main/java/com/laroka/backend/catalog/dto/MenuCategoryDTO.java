package com.laroka.backend.catalog.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuCategoryDTO {
	private Integer categoryId;
	private String categoryName;
	// US-HH-F-01: habilita la opción "mitad y mitad" en el detalle de producto del client.
	// false cuando la categoría no tiene tipo asignado (FK nullable, ver US-CAT-02).
	private boolean allowsHalfAndHalf;
	private List<MenuProductDTO> products;
}
