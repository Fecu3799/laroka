package com.laroka.backend.catalog.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.laroka.backend.catalog.entity.BranchProductSize;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.repository.BranchProductSizeRepository;
import com.laroka.backend.catalog.repository.ProductSizeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductSizeService {

	private final BranchProductSizeRepository branchProductSizeRepository;
	private final ProductSizeRepository productSizeRepository;

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

	/**
	 * US-SIZE-F-02: tamaños activos de la sucursal, agrupados por producto y con el precio ya
	 * resuelto por la fórmula de US-SIZE-02. Dos queries en total (tamaños + overrides),
	 * independientemente de cuántos productos tenga el menú.
	 *
	 * Los productos sin tamaños activos simplemente no aparecen en el mapa.
	 */
	public Map<Integer, List<ResolvedProductSize>> resolveSizesForBranch(Integer branchId) {
		List<ProductSize> sizes = productSizeRepository.findActiveByBranchId(branchId);
		if (sizes.isEmpty()) {
			return Map.of();
		}

		Map<Integer, BigDecimal> overrides = branchProductSizeRepository.findByBranchId(branchId).stream()
			.filter(bps -> bps.getPriceOverride() != null)
			.collect(Collectors.toMap(bps -> bps.getProductSize().getId(),
				BranchProductSize::getPriceOverride));

		return sizes.stream().collect(Collectors.groupingBy(
			ps -> ps.getProduct().getId(),
			Collectors.mapping(
				ps -> new ResolvedProductSize(ps.getId(), ps.getSize(),
					overrides.getOrDefault(ps.getId(), ps.getPrice())),
				Collectors.toList())));
	}
}
