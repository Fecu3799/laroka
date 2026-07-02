package com.laroka.backend.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.dto.MenuCategoryDTO;
import com.laroka.backend.catalog.dto.MenuProductDTO;
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
		return branchProduct(1, "Muzzarella", basePrice, priceOverride, true);
	}

	private BranchProduct branchProduct(Integer id, String name, BigDecimal basePrice,
			BigDecimal priceOverride, boolean available) {
		Category category = Category.builder().id(1).name("Pizzas").build();
		Product product = Product.builder()
			.id(id)
			.name(name)
			.description("Clásica")
			.price(basePrice)
			.category(category)
			.build();
		return BranchProduct.builder()
			.branch(Branch.builder().id(1).build())
			.product(product)
			.available(available)
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

	// US-15-11: el menú incluye productos no disponibles con el flag available,
	// y los ordena disponibles primero / no disponibles al final dentro de cada categoría.

	@Test
	void toMenu_includesUnavailableProducts_withAvailableFlagPerProduct() {
		BranchProduct available = branchProduct(1, "Muzzarella", new BigDecimal("2800.00"), null, true);
		BranchProduct unavailable = branchProduct(2, "Napolitana", new BigDecimal("3200.00"), null, false);

		List<MenuCategoryDTO> menu = mapper.toMenu(List.of(available, unavailable));

		assertThat(menu).hasSize(1);
		assertThat(menu.get(0).getProducts()).hasSize(2);
		assertThat(menu.get(0).getProducts())
			.extracting("id", "available")
			.containsExactlyInAnyOrder(
				tuple(1, true),
				tuple(2, false));
	}

	@Test
	void toMenu_ordersAvailableFirstThenUnavailable() {
		// Entra el no disponible primero: el mapper debe reordenar dejando el disponible al frente.
		BranchProduct unavailable = branchProduct(2, "Napolitana", new BigDecimal("3200.00"), null, false);
		BranchProduct available = branchProduct(1, "Muzzarella", new BigDecimal("2800.00"), null, true);

		List<MenuCategoryDTO> menu = mapper.toMenu(List.of(unavailable, available));

		assertThat(menu).hasSize(1);
		assertThat(menu.get(0).getProducts())
			.extracting(MenuProductDTO::getAvailable)
			.containsExactly(true, false);
	}
}
