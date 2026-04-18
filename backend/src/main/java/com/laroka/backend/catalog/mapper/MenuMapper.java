package com.laroka.backend.catalog.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.MenuCategoryDTO;
import com.laroka.backend.catalog.dto.MenuProductDTO;
import com.laroka.backend.catalog.entity.Product;

@Component
public class MenuMapper {

	public List<MenuCategoryDTO> toMenu(List<Product> products) {
		return products.stream()
			.collect(Collectors.groupingBy(p -> p.getCategory().getId()))
			.entrySet().stream()
			.map(entry -> {
				var category = entry.getValue().get(0).getCategory();
				return MenuCategoryDTO.builder()
					.categoryId(category.getId())
					.categoryName(category.getName())
					.products(entry.getValue().stream()
						.map(this::toMenuProductDTO)
						.toList())
					.build();
			})
			.toList();
	}

	private MenuProductDTO toMenuProductDTO(Product product) {
		return MenuProductDTO.builder()
			.id(product.getId())
			.name(product.getName())
			.description(product.getDescription())
			.price(product.getPrice())
			.imageUrl(product.getImageUrl())
			.build();
	}
}
