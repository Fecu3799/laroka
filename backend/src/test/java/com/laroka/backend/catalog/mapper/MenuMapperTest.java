package com.laroka.backend.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.dto.MenuCategoryDTO;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.Product;

/**
 * US-14-02: el menú público expone el precio ya resuelto
 * (priceOverride != null ? priceOverride : product.price). El cliente nunca
 * sabe si el precio es base u override.
 */
class MenuMapperTest {

	private final MenuMapper mapper = new MenuMapper();

	private BranchProduct branchProduct(BigDecimal basePrice, BigDecimal priceOverride) {
		Category category = Category.builder().id(1).name("Pizzas").build();
		Product product = Product.builder()
			.id(1)
			.name("Muzzarella")
			.description("Clásica")
			.price(basePrice)
			.category(category)
			.build();
		return BranchProduct.builder()
			.branch(Branch.builder().id(1).build())
			.product(product)
			.available(true)
			.priceOverride(priceOverride)
			.build();
	}

	@Test
	void toMenu_withPriceOverride_resolvesToOverride() {
		BranchProduct bp = branchProduct(new BigDecimal("2800.00"), new BigDecimal("3100.00"));

		List<MenuCategoryDTO> menu = mapper.toMenu(List.of(bp));

		assertThat(menu).hasSize(1);
		assertThat(menu.get(0).getProducts()).hasSize(1);
		assertThat(menu.get(0).getProducts().get(0).getPrice()).isEqualByComparingTo("3100.00");
	}

	@Test
	void toMenu_withoutPriceOverride_resolvesToBasePrice() {
		BranchProduct bp = branchProduct(new BigDecimal("2800.00"), null);

		List<MenuCategoryDTO> menu = mapper.toMenu(List.of(bp));

		assertThat(menu).hasSize(1);
		assertThat(menu.get(0).getProducts()).hasSize(1);
		assertThat(menu.get(0).getProducts().get(0).getPrice()).isEqualByComparingTo("2800.00");
	}
}
