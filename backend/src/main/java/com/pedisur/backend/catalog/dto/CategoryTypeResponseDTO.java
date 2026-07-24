package com.pedisur.backend.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * US-CAT-03: tipo de categoría maestro expuesto al backoffice. allowsHalfAndHalf lo
 * consume el flujo mitad y mitad (US-HH); allowsSizes el de tamaños (US-SIZE).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTypeResponseDTO {
	private Integer id;
	private String name;
	private boolean allowsHalfAndHalf;
	private boolean allowsSizes;
}
