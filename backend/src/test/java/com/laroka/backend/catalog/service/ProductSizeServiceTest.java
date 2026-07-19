package com.laroka.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.catalog.entity.BranchProductSize;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.entity.ProductSizeName;
import com.laroka.backend.catalog.repository.BranchProductSizeRepository;

@ExtendWith(MockitoExtension.class)
class ProductSizeServiceTest {

	private static final int BRANCH_ID = 1;
	private static final int SIZE_ID = 10;

	@Mock
	private BranchProductSizeRepository branchProductSizeRepository;

	@InjectMocks
	private ProductSizeService service;

	private final ProductSize grande = ProductSize.builder()
		.id(SIZE_ID)
		.size(ProductSizeName.GRANDE)
		.price(new BigDecimal("15000.00"))
		.active(true)
		.build();

	@Test
	void usesOverrideWhenBranchHasOne() {
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.of(BranchProductSize.builder()
				.priceOverride(new BigDecimal("17500.00"))
				.build()));

		assertThat(service.resolveEffectivePrice(BRANCH_ID, grande)).isEqualByComparingTo("17500.00");
	}

	@Test
	void fallsBackToBasePriceWhenNoRowForBranch() {
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.empty());

		assertThat(service.resolveEffectivePrice(BRANCH_ID, grande)).isEqualByComparingTo("15000.00");
	}

	@Test
	void fallsBackToBasePriceWhenRowExistsWithNullOverride() {
		// Fila presente pero sin override: equivalente a no tener fila. Este es el caso que
		// deja el backoffice al limpiar un override ya cargado (mismo criterio que
		// BranchProduct.priceOverride = null en US-14-02).
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.of(BranchProductSize.builder()
				.priceOverride(null)
				.build()));

		assertThat(service.resolveEffectivePrice(BRANCH_ID, grande)).isEqualByComparingTo("15000.00");
	}
}
