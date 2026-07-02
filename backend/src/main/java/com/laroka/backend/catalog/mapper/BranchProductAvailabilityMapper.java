package com.laroka.backend.catalog.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.BranchProductAvailabilityDTO;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Product;

@Component
public class BranchProductAvailabilityMapper {

	public List<BranchProductAvailabilityDTO> toList(List<BranchProduct> branchProducts) {
		return branchProducts.stream()
			.map(this::toDTO)
			.toList();
	}

	private BranchProductAvailabilityDTO toDTO(BranchProduct branchProduct) {
		Product product = branchProduct.getProduct();
		return BranchProductAvailabilityDTO.builder()
			.productId(product.getId())
			.name(product.getName())
			.categoryId(product.getCategory().getId())
			.categoryName(product.getCategory().getName())
			.imageUrl(product.getImageUrl())
			.available(branchProduct.getAvailable())
			.build();
	}
}
