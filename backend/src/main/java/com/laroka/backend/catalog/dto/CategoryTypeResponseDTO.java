package com.laroka.backend.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * US-CAT-03: tipo de categoría maestro expuesto al backoffice. allowsHalfAndHalf lo
 * consume el flujo mitad y mitad (US-HH).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTypeResponseDTO {
	private Integer id;
	private String name;
	private boolean allowsHalfAndHalf;
}
