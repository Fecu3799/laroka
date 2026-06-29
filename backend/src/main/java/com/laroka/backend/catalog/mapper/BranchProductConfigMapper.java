package com.laroka.backend.catalog.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.BranchProductConfigDTO;
import com.laroka.backend.catalog.entity.BranchProduct;

@Component
public class BranchProductConfigMapper {

	public List<BranchProductConfigDTO> toConfigList(List<BranchProduct> branchProducts) {
		return branchProducts.stream()
			.map(this::toConfigDTO)
			.toList();
	}

	private BranchProductConfigDTO toConfigDTO(BranchProduct branchProduct) {
		BigDecimal effectivePrice = branchProduct.getPriceOverride() != null
			? branchProduct.getPriceOverride()
			: branchProduct.getProduct().getPrice();
		return BranchProductConfigDTO.builder()
			.branchId(branchProduct.getBranch().getId())
			.branchName(branchProduct.getBranch().getName())
			.available(branchProduct.getAvailable())
			.priceOverride(branchProduct.getPriceOverride())
			.effectivePrice(effectivePrice)
			.build();
	}
}
