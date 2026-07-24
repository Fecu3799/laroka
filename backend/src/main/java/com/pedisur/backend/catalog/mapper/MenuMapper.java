package com.pedisur.backend.catalog.mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.pedisur.backend.catalog.dto.MenuCategoryDTO;
import com.pedisur.backend.catalog.dto.MenuProductDTO;
import com.pedisur.backend.catalog.dto.MenuProductSizeDTO;
import com.pedisur.backend.catalog.entity.BranchProduct;
import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.catalog.service.BranchMenu;
import com.pedisur.backend.catalog.service.ResolvedProductSize;

@Component
public class MenuMapper {

	public List<MenuCategoryDTO> toMenu(BranchMenu menu) {
		var sizesByProductId = menu.sizesByProductId();
		return menu.branchProducts().stream()
			.collect(Collectors.groupingBy(bp -> bp.getProduct().getCategory().getId()))
			.entrySet().stream()
			.map(entry -> {
				var category = entry.getValue().get(0).getProduct().getCategory();
				return MenuCategoryDTO.builder()
					.categoryId(category.getId())
					.categoryName(category.getName())
					// US-HH-F-01: categoryType viene del LEFT JOIN FETCH de la query del menú.
					// Null (categoría sin tipo asignado) → false, no habilita mitad y mitad.
					.allowsHalfAndHalf(category.getCategoryType() != null
						&& category.getCategoryType().isAllowsHalfAndHalf())
					// US-SIZE-F-02: mismo criterio, flag independiente.
					.allowsSizes(category.getCategoryType() != null
						&& category.getCategoryType().isAllowsSizes())
					.products(entry.getValue().stream()
						// US-15-11: dentro de cada categoría, productos disponibles
						// primero y no disponibles al final. Sort estable: preserva el
						// orden por nombre que trae la query entre ítems con igual availability.
						.sorted(Comparator.comparing(BranchProduct::getAvailable, Comparator.reverseOrder()))
						.map(bp -> toMenuProductDTO(bp, sizesByProductId))
						.toList())
					.build();
			})
			.toList();
	}

	private MenuProductDTO toMenuProductDTO(BranchProduct branchProduct,
			Map<Integer, List<ResolvedProductSize>> sizesByProductId) {
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
			// US-SIZE-F-02: lista vacía (no null) para que el client no tenga que defenderse.
			.sizes(sizesByProductId.getOrDefault(product.getId(), List.of()).stream()
				.map(rs -> MenuProductSizeDTO.builder()
					.id(rs.id())
					.size(rs.size().name())
					.price(rs.price())
					.build())
				.toList())
			.build();
	}
}
