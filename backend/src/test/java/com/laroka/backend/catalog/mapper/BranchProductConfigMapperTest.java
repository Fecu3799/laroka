package com.laroka.backend.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.dto.BranchProductConfigDTO;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Product;

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

	@Test
	void toConfigList_partialOverrides_resolvesEffectivePricePerBranch() {
		Product product = Product.builder().id(1).name("Muzzarella").price(new BigDecimal("2800.00")).build();

		BranchProduct withOverride = branchProduct(1, "Playa Unión", product, true, new BigDecimal("3100.00"));
		BranchProduct withoutOverride = branchProduct(2, "Puerto Madryn", product, false, null);

		List<BranchProductConfigDTO> result = mapper.toConfigList(List.of(withOverride, withoutOverride));

		assertThat(result)
			.extracting("branchId", "branchName", "available", "priceOverride", "effectivePrice")
			.containsExactly(
				tuple(1, "Playa Unión", true, new BigDecimal("3100.00"), new BigDecimal("3100.00")),
				tuple(2, "Puerto Madryn", false, null, new BigDecimal("2800.00")));
	}
}
