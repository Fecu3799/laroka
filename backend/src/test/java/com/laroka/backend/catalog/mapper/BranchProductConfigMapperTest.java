package com.laroka.backend.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.dto.BranchProductConfigDTO;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.entity.ProductSizeName;
import com.laroka.backend.catalog.service.ProductBranchConfig;

/**
 * US-14-03: configuración por sucursal de un producto. Un producto con overrides
 * parciales retorna la lista correcta con el precio efectivo (override ?? precio base)
 * para todas las sucursales.
 */
class BranchProductConfigMapperTest {

	private final BranchProductConfigMapper mapper = new BranchProductConfigMapper();

	private BranchProduct branchProduct(Integer branchId, String branchName, Product product,
			Boolean available, BigDecimal priceOverride) {
		return BranchProduct.builder()
			.branch(Branch.builder().id(branchId).name(branchName).build())
			.product(product)
			.available(available)
			.priceOverride(priceOverride)
			.build();
	}

	// Producto sin tamaño cargado: el caso de todo lo que no es pizza.
	private ProductBranchConfig withoutSize(List<BranchProduct> rows) {
		return new ProductBranchConfig(rows, null, Map.of());
	}

	@Test
	void toConfigList_partialOverrides_resolvesEffectivePricePerBranch() {
		Product product = Product.builder().id(1).name("Muzzarella").price(new BigDecimal("2800.00")).build();

		BranchProduct withOverride = branchProduct(1, "Playa Unión", product, true, new BigDecimal("3100.00"));
		BranchProduct withoutOverride = branchProduct(2, "Puerto Madryn", product, false, null);

		List<BranchProductConfigDTO> result =
			mapper.toConfigList(withoutSize(List.of(withOverride, withoutOverride)));

		assertThat(result)
			.extracting("branchId", "branchName", "available", "priceOverride", "effectivePrice")
			.containsExactly(
				tuple(1, "Playa Unión", true, new BigDecimal("3100.00"), new BigDecimal("3100.00")),
				tuple(2, "Puerto Madryn", false, null, new BigDecimal("2800.00")));
	}

	// US-SIZE-F-01: el tamaño activo y su override por sucursal viajan en la misma fila.

	@Test
	void toConfigList_withoutSize_leavesSizeFieldsNull() {
		Product product = Product.builder().id(1).name("Gaseosa").price(new BigDecimal("3000.00")).build();

		List<BranchProductConfigDTO> result =
			mapper.toConfigList(withoutSize(List.of(branchProduct(1, "Centro", product, true, null))));

		assertThat(result.get(0).getProductSizeId()).isNull();
		assertThat(result.get(0).getSizePriceOverride()).isNull();
		assertThat(result.get(0).getSizeEffectivePrice()).isNull();
	}

	@Test
	void toConfigList_withSize_resolvesSizePricePerBranch() {
		Product product = Product.builder().id(1).name("Muzzarella").price(new BigDecimal("15000.00")).build();
		ProductSize chica = ProductSize.builder()
			.id(50).product(product).size(ProductSizeName.CHICA)
			.price(new BigDecimal("9000.00")).active(true)
			.build();

		// Sólo la sucursal 1 tiene override del tamaño; la 2 usa el precio base del tamaño.
		ProductBranchConfig config = new ProductBranchConfig(
			List.of(branchProduct(1, "Centro", product, true, null),
				branchProduct(2, "Norte", product, true, null)),
			chica,
			Map.of(1, new BigDecimal("9900.00")));

		List<BranchProductConfigDTO> result = mapper.toConfigList(config);

		assertThat(result)
			.extracting("branchId", "productSizeId", "sizePriceOverride", "sizeEffectivePrice")
			.containsExactly(
				tuple(1, 50, new BigDecimal("9900.00"), new BigDecimal("9900.00")),
				tuple(2, 50, null, new BigDecimal("9000.00")));
	}

	@Test
	void toConfigList_sizeOverrideIsIndependentFromProductOverride() {
		// Un producto puede tener override de precio entero sin tenerlo del tamaño, y viceversa.
		Product product = Product.builder().id(1).name("Muzzarella").price(new BigDecimal("15000.00")).build();
		ProductSize chica = ProductSize.builder()
			.id(50).product(product).size(ProductSizeName.CHICA)
			.price(new BigDecimal("9000.00")).active(true)
			.build();

		ProductBranchConfig config = new ProductBranchConfig(
			List.of(branchProduct(1, "Centro", product, true, new BigDecimal("16000.00"))),
			chica,
			Map.of());

		BranchProductConfigDTO row = mapper.toConfigList(config).get(0);

		assertThat(row.getEffectivePrice()).isEqualByComparingTo("16000.00");
		assertThat(row.getSizeEffectivePrice()).isEqualByComparingTo("9000.00");
		assertThat(row.getSizePriceOverride()).isNull();
	}
}
