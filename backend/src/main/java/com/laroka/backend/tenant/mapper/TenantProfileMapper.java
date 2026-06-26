package com.laroka.backend.tenant.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.tenant.dto.TenantProfilePublicDTO;
import com.laroka.backend.tenant.entity.TenantProfile;

@Component
public class TenantProfileMapper {

	public TenantProfilePublicDTO toPublicDTO(TenantProfile profile) {
		if (profile == null) {
			return null;
		}
		return TenantProfilePublicDTO.builder()
			.businessName(profile.getBusinessName())
			.description(profile.getDescription())
			.instagramUrl(profile.getInstagramUrl())
			.facebookUrl(profile.getFacebookUrl())
			.whatsapp(profile.getWhatsapp())
			.logoUrl(profile.getLogoUrl())
			.build();
	}
}
