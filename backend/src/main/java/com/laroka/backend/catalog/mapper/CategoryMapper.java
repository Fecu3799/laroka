package com.laroka.backend.catalog.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.CategoryRequestDTO;
import com.laroka.backend.catalog.dto.CategoryResponseDTO;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.mapper.TenantMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CategoryMapper {

	private final TenantMapper tenantMapper;

	public CategoryResponseDTO toResponseDTO(Category category) {
		if (category == null) {
			return null;
		}
		return CategoryResponseDTO.builder()
			.id(category.getId())
			.name(category.getName())
			.tenantId(category.getTenant().getId())
			.tenant(tenantMapper.toResponseDTO(category.getTenant()))
			.createdAt(category.getCreatedAt())
			.updatedAt(category.getUpdatedAt())
			.build();
	}

	public Category toEntity(CategoryRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return Category.builder()
			.name(dto.getName())
			.tenant(Tenant.builder().id(dto.getTenantId()).build())
			.build();
	}
}
