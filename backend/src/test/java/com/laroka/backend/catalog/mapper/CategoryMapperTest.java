package com.laroka.backend.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laroka.backend.catalog.dto.CategoryResponseDTO;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.tenant.entity.Tenant;

/**
 * US-14-05: el listado de categorías expone productCount. Una categoría sin
 * productos (ausente del mapa de conteos) resuelve a productCount = 0.
 */
class CategoryMapperTest {

	private final CategoryMapper mapper = new CategoryMapper();

	private Category category(Integer id, String name) {
		return Category.builder()
			.id(id)
			.name(name)
			.tenant(Tenant.builder().id(1).name("LaRoka").build())
			.build();
	}

	@Test
	void toResponseDTOList_categoryWithoutProducts_productCountZero() {
		Category empty = category(1, "Pizzas");

		List<CategoryResponseDTO> result = mapper.toResponseDTOList(List.of(empty), Map.of());

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getProductCount()).isZero();
	}

	@Test
	void toResponseDTOList_categoryWithProducts_productCountFromMap() {
		Category withProducts = category(1, "Pizzas");
		Category empty = category(2, "Empanadas");

		List<CategoryResponseDTO> result = mapper.toResponseDTOList(
			List.of(withProducts, empty), Map.of(1, 4L));

		assertThat(result)
			.extracting(CategoryResponseDTO::getId, CategoryResponseDTO::getProductCount)
			.containsExactly(
				tuple(1, 4),
				tuple(2, 0));
	}
}
