package com.pedisur.backend.tenant.mapper;

import org.springframework.stereotype.Component;

import com.pedisur.backend.tenant.dto.TenantRequestDTO;
import com.pedisur.backend.tenant.dto.TenantResponseDTO;
import com.pedisur.backend.tenant.entity.Tenant;

@Component
public class TenantMapper {

	public TenantResponseDTO toResponseDTO(Tenant tenant) {
		if (tenant == null) {
			return null;
		}
		return TenantResponseDTO.builder()
			.id(tenant.getId())
			.name(tenant.getName())
			.createdAt(tenant.getCreatedAt())
			.updatedAt(tenant.getUpdatedAt())
			.build();
	}

	public Tenant toEntity(TenantRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return Tenant.builder()
			.name(dto.getName())
			.build();
	}
}
