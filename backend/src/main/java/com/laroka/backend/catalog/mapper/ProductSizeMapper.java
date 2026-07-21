package com.laroka.backend.catalog.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.ProductSizeResponseDTO;
import com.laroka.backend.catalog.entity.ProductSize;

@Component
public class ProductSizeMapper {

	public ProductSizeResponseDTO toResponseDTO(ProductSize size) {
		if (size == null) {
			return null;
		}
		return ProductSizeResponseDTO.builder()
			.id(size.getId())
			// product es lazy: getId() lo lee del proxy sin inicializarlo.
			.productId(size.getProduct().getId())
			.size(size.getSize().name())
			.price(size.getPrice())
			.active(Boolean.TRUE.equals(size.getActive()))
			.build();
	}

	public List<ProductSizeResponseDTO> toResponseDTOList(List<ProductSize> sizes) {
		return sizes.stream().map(this::toResponseDTO).toList();
	}
}
