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
public class TenantProfileResponseDTO {
	private Integer id;
	private String businessName;
	private String description;
	private String instagramUrl;
	private String facebookUrl;
	private String whatsapp;
	private String logoUrl;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
