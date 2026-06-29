package com.laroka.backend.catalog.mapper;

import java.util.List;
import java.util.Map;

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
		return toResponseDTO(category, null);
	}

	public CategoryResponseDTO toResponseDTO(Category category, Integer productCount) {
		if (category == null) {
			return null;
		}
		return CategoryResponseDTO.builder()
			.id(category.getId())
			.name(category.getName())
			.tenantId(category.getTenant().getId())
			.tenant(tenantMapper.toResponseDTO(category.getTenant()))
			.productCount(productCount)
			.createdAt(category.getCreatedAt())
			.updatedAt(category.getUpdatedAt())
			.build();
	}

	// US-14-05: mapea cada categoría con su cantidad de productos. Una categoría ausente del
	// mapa de conteos (sin productos) resuelve a productCount = 0.
	public List<CategoryResponseDTO> toResponseDTOList(List<Category> categories, Map<Integer, Long> productCounts) {
		return categories.stream()
			.map(category -> toResponseDTO(category, productCounts.getOrDefault(category.getId(), 0L).intValue()))
			.toList();
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
