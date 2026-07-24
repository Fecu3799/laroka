package com.pedisur.backend.catalog.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.pedisur.backend.catalog.dto.BranchProductConfigDTO;
import com.pedisur.backend.catalog.entity.BranchProduct;
import com.pedisur.backend.catalog.entity.ProductSize;
import com.pedisur.backend.catalog.service.ProductBranchConfig;

@Component
public class BranchProductConfigMapper {

	public List<BranchProductConfigDTO> toConfigList(ProductBranchConfig config) {
		ProductSize size = config.activeSize();
		Map<Integer, BigDecimal> sizeOverrides = config.sizeOverridesByBranchId();
		return config.branchProducts().stream()
			.map(bp -> toConfigDTO(bp, size, sizeOverrides))
			.toList();
	}

	private BranchProductConfigDTO toConfigDTO(BranchProduct branchProduct, ProductSize size,
			Map<Integer, BigDecimal> sizeOverrides) {
		BigDecimal effectivePrice = branchProduct.getPriceOverride() != null
			? branchProduct.getPriceOverride()
			: branchProduct.getProduct().getPrice();
		Integer branchId = branchProduct.getBranch().getId();

		// US-SIZE-F-01: mismo criterio de resolución que el precio del producto, pero sobre el
		// tamaño activo. Sin tamaño cargado, los tres campos quedan en null.
		BigDecimal sizeOverride = size != null ? sizeOverrides.get(branchId) : null;
		BigDecimal sizeEffectivePrice = size == null
			? null
			: (sizeOverride != null ? sizeOverride : size.getPrice());

		return BranchProductConfigDTO.builder()
			.branchId(branchId)
			.branchName(branchProduct.getBranch().getName())
			.available(branchProduct.getAvailable())
			.priceOverride(branchProduct.getPriceOverride())
			.effectivePrice(effectivePrice)
			.productSizeId(size != null ? size.getId() : null)
			.sizePriceOverride(sizeOverride)
			.sizeEffectivePrice(sizeEffectivePrice)
			.build();
	}
}
