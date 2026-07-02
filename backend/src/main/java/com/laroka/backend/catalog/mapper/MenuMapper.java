package com.laroka.backend.catalog.mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.MenuCategoryDTO;
import com.laroka.backend.catalog.dto.MenuProductDTO;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Product;

@Component
public class MenuMapper {

	public List<MenuCategoryDTO> toMenu(List<BranchProduct> branchProducts) {
		return branchProducts.stream()
			.collect(Collectors.groupingBy(bp -> bp.getProduct().getCategory().getId()))
			.entrySet().stream()
			.map(entry -> {
				var category = entry.getValue().get(0).getProduct().getCategory();
				return MenuCategoryDTO.builder()
					.categoryId(category.getId())
					.categoryName(category.getName())
					.products(entry.getValue().stream()
						// US-15-11: dentro de cada categoría, productos disponibles
						// primero y no disponibles al final. Sort estable: preserva el
						// orden por nombre que trae la query entre ítems con igual availability.
						.sorted(Comparator.comparing(BranchProduct::getAvailable, Comparator.reverseOrder()))
						.map(this::toMenuProductDTO)
						.toList())
					.build();
			})
			.toList();
	}

	private MenuProductDTO toMenuProductDTO(BranchProduct branchProduct) {
		Product product = branchProduct.getProduct();
		BigDecimal effectivePrice = branchProduct.getPriceOverride() != null
			? branchProduct.getPriceOverride()
			: product.getPrice();
		return MenuProductDTO.builder()
			.id(product.getId())
			.name(product.getName())
			.description(product.getDescription())
			.price(effectivePrice)
			.imageUrl(product.getImageUrl())
			.available(branchProduct.getAvailable())
			.build();
	}
}
