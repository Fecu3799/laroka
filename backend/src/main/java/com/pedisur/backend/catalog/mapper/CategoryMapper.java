package com.pedisur.backend.catalog.mapper;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.pedisur.backend.catalog.dto.CategoryRequestDTO;
import com.pedisur.backend.catalog.dto.CategoryResponseDTO;
import com.pedisur.backend.catalog.entity.Category;
import com.pedisur.backend.catalog.entity.CategoryType;
import com.pedisur.backend.tenant.entity.Tenant;

@Component
public class CategoryMapper {

	public CategoryResponseDTO toResponseDTO(Category category) {
		return toResponseDTO(category, null);
	}

	public CategoryResponseDTO toResponseDTO(Category category, Integer productCount) {
		if (category == null) {
			return null;
		}
		// US-CAT-03: categoryType puede ser null (categorías aún sin reasignar). Debe venir
		// inicializado desde la query (@EntityGraph en CategoryRepository) para poder leer su
		// name acá, fuera de la sesión, sin LazyInitializationException.
		CategoryType categoryType = category.getCategoryType();
		return CategoryResponseDTO.builder()
			.id(category.getId())
			.name(category.getName())
			.tenantId(category.getTenant().getId())
			.categoryTypeId(categoryType != null ? categoryType.getId() : null)
			.categoryTypeName(categoryType != null ? categoryType.getName() : null)
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
			.categoryType(CategoryType.builder().id(dto.getCategoryTypeId()).build())
			.build();
	}
}
