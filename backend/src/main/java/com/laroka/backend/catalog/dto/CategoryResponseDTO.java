package com.laroka.backend.catalog.dto;

import java.time.LocalDateTime;

import com.laroka.backend.tenant.dto.TenantResponseDTO;

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
	private TenantResponseDTO tenant;
	// Cantidad de productos asociados a la categoría (US-14-05).
	private Integer productCount;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
