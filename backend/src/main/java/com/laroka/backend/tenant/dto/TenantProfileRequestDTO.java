package com.laroka.backend.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantProfileRequestDTO {

	@NotBlank(message = "Business name is required")
	private String businessName;

	@NotBlank(message = "Description is required")
	private String description;

	private String instagramUrl;
	private String facebookUrl;
	private String whatsapp;
	private String logoUrl;
}
