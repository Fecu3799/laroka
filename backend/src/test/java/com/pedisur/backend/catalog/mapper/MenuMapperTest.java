package com.pedisur.backend.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.catalog.dto.MenuCategoryDTO;
import com.pedisur.backend.catalog.dto.MenuProductDTO;
import com.pedisur.backend.catalog.entity.BranchProduct;
import com.pedisur.backend.catalog.entity.Category;
import com.pedisur.backend.catalog.entity.CategoryType;
import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.catalog.entity.ProductSizeName;
import com.pedisur.backend.catalog.service.BranchMenu;
import com.pedisur.backend.catalog.service.ResolvedProductSize;

/**
 * US-14-02: el menú público expone el precio ya resuelto
 * (priceOverride != null ? priceOverride : product.price). El cliente nunca
 * sabe si el precio es base u override.
 */
class MenuMapperTest {

	private final MenuMapper mapper = new MenuMapper();

	// Menú sin tamaños: el caso por defecto de casi todos estos tests.
	private BranchMenu menuOf(List<BranchProduct> branchProducts) {
		return new BranchMenu(branchProducts, Map.of());
	}

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

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(bp)));

		assertThat(menu).hasSize(1);
		assertThat(menu.get(0).getProducts()).hasSize(1);
		assertThat(menu.get(0).getProducts().get(0).getPrice()).isEqualByComparingTo("3100.00");
	}

	@Test
	void toMenu_withoutPriceOverride_resolvesToBasePrice() {
		BranchProduct bp = branchProduct(new BigDecimal("2800.00"), null);

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(bp)));

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

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(available, unavailable)));

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

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(unavailable, available)));

		assertThat(menu).hasSize(1);
		assertThat(menu.get(0).getProducts())
			.extracting(MenuProductDTO::getAvailable)
			.containsExactly(true, false);
	}

	// US-HH-F-01: el menú expone allowsHalfAndHalf por categoría para que el client sepa
	// en qué productos ofrecer la opción de mitad y mitad.

	private BranchProduct branchProductWithType(CategoryType categoryType) {
		Category category = Category.builder().id(1).name("Pizzas").categoryType(categoryType).build();
		Product product = Product.builder()
			.id(1).name("Muzzarella").price(new BigDecimal("2800.00")).category(category)
			.build();
		return BranchProduct.builder()
			.branch(Branch.builder().id(1).build())
			.product(product)
			.available(true)
			.build();
	}

	@Test
	void toMenu_categoryTypeAllowsHalfAndHalf_exposesFlagTrue() {
		CategoryType pizza = CategoryType.builder().id(7).name("Pizza").allowsHalfAndHalf(true).active(true).build();

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(branchProductWithType(pizza))));

		assertThat(menu.get(0).isAllowsHalfAndHalf()).isTrue();
	}

	@Test
	void toMenu_categoryTypeDoesNotAllowHalfAndHalf_exposesFlagFalse() {
		CategoryType bebida = CategoryType.builder().id(8).name("Bebida").allowsHalfAndHalf(false).active(true).build();

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(branchProductWithType(bebida))));

		assertThat(menu.get(0).isAllowsHalfAndHalf()).isFalse();
	}

	@Test
	void toMenu_categoryWithoutType_exposesFlagFalseWithoutFailing() {
		// Categoría anterior a US-CAT-02, sin tipo asignado: el FK es nullable y el menú
		// debe seguir respondiendo, no romper con NPE.
		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(branchProductWithType(null))));

		assertThat(menu).hasSize(1);
		assertThat(menu.get(0).isAllowsHalfAndHalf()).isFalse();
	}

	// US-SIZE-F-02: el menú expone allowsSizes por categoría y los tamaños por producto, con
	// el precio de sucursal ya resuelto.

	@Test
	void toMenu_exposesAllowsSizesIndependentlyOfHalfAndHalf() {
		CategoryType soloTamanios = CategoryType.builder()
			.id(9).name("Milanesa").allowsHalfAndHalf(false).allowsSizes(true).active(true).build();

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(branchProductWithType(soloTamanios))));

		assertThat(menu.get(0).isAllowsSizes()).isTrue();
		assertThat(menu.get(0).isAllowsHalfAndHalf()).isFalse();
	}

	@Test
	void toMenu_categoryWithoutType_exposesAllowsSizesFalse() {
		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(branchProductWithType(null))));

		assertThat(menu.get(0).isAllowsSizes()).isFalse();
	}

	@Test
	void toMenu_productWithSizes_exposesThemWithResolvedPrice() {
		BranchProduct bp = branchProduct(1, "Muzzarella", new BigDecimal("15000.00"), null, true);
		BranchMenu menuData = new BranchMenu(List.of(bp), Map.of(1, List.of(
			new ResolvedProductSize(50, ProductSizeName.CHICA, new BigDecimal("9000.00")))));

		List<MenuCategoryDTO> menu = mapper.toMenu(menuData);

		var sizes = menu.get(0).getProducts().get(0).getSizes();
		assertThat(sizes).hasSize(1);
		assertThat(sizes.get(0).getId()).isEqualTo(50);
		assertThat(sizes.get(0).getSize()).isEqualTo("CHICA");
		assertThat(sizes.get(0).getPrice()).isEqualByComparingTo("9000.00");
	}

	@Test
	void toMenu_productWithoutSizes_exposesEmptyListNotNull() {
		// El client no debería tener que defenderse de un null acá.
		BranchProduct bp = branchProduct(new BigDecimal("2800.00"), null);

		List<MenuCategoryDTO> menu = mapper.toMenu(menuOf(List.of(bp)));

		assertThat(menu.get(0).getProducts().get(0).getSizes()).isEmpty();
	}
}
