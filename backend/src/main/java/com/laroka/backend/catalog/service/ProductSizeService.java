package com.laroka.backend.catalog.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.laroka.backend.catalog.entity.BranchProductSize;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.repository.BranchProductSizeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductSizeService {

	private final BranchProductSizeRepository branchProductSizeRepository;

	/**
	 * US-SIZE-02: precio efectivo de un tamaño en una sucursal —
	 * {@code branch_product_size.price_override ?? product_size.price}.
	 *
	 * Mismo criterio que el precio efectivo del producto sin tamaño
	 * (OrderService.effectivePrice): sin fila de override, o con la fila pero
	 * price_override en NULL, vale el precio base del tamaño.
	 */
	public BigDecimal resolveEffectivePrice(Integer branchId, ProductSize productSize) {
		BigDecimal override = branchProductSizeRepository
			.findByBranchIdAndProductSizeId(branchId, productSize.getId())
			.map(BranchProductSize::getPriceOverride)
			.orElse(null);
		return override != null ? override : productSize.getPrice();
	}
}
