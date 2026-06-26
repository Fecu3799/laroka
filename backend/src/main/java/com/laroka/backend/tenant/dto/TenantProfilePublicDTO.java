package com.laroka.backend.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantProfilePublicDTO {
	private String businessName;
	private String description;
	private String instagramUrl;
	private String facebookUrl;
	private String whatsapp;
	private String logoUrl;
}
