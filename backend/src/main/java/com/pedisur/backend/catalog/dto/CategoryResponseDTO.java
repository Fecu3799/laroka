package com.pedisur.backend.catalog.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponseDTO {
	private Integer id;
	private String name;
	private Integer tenantId;
	// US-CAT-03: tipo maestro asignado. Null en categorías aún sin reasignar (ver KNOWN_ISSUES.md).
	private Integer categoryTypeId;
	private String categoryTypeName;
	// Cantidad de productos asociados a la categoría (US-14-05).
	private Integer productCount;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
