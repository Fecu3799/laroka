package com.pedisur.backend.tenant.mapper;

import org.springframework.stereotype.Component;

import com.pedisur.backend.tenant.dto.TenantProfilePublicDTO;
import com.pedisur.backend.tenant.dto.TenantProfileRequestDTO;
import com.pedisur.backend.tenant.dto.TenantProfileResponseDTO;
import com.pedisur.backend.tenant.entity.TenantProfile;

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

	public TenantProfileResponseDTO toResponseDTO(TenantProfile profile) {
		if (profile == null) {
			return null;
		}
		return TenantProfileResponseDTO.builder()
			.id(profile.getId())
			.businessName(profile.getBusinessName())
			.description(profile.getDescription())
			.instagramUrl(profile.getInstagramUrl())
			.facebookUrl(profile.getFacebookUrl())
			.whatsapp(profile.getWhatsapp())
			.logoUrl(profile.getLogoUrl())
			.createdAt(profile.getCreatedAt())
			.updatedAt(profile.getUpdatedAt())
			.build();
	}

	public TenantProfile toEntity(TenantProfileRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return TenantProfile.builder()
			.businessName(dto.getBusinessName())
			.description(dto.getDescription())
			.instagramUrl(dto.getInstagramUrl())
			.facebookUrl(dto.getFacebookUrl())
			.whatsapp(dto.getWhatsapp())
			.logoUrl(dto.getLogoUrl())
			.build();
	}
}
