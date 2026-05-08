package com.laroka.backend.tenant.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantResponseDTO {
	private Integer id;
	private String name;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
