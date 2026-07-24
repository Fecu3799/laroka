package com.pedisur.backend.catalog.mapper;

import org.springframework.stereotype.Component;

import com.pedisur.backend.catalog.dto.ProductRequestDTO;
import com.pedisur.backend.catalog.dto.ProductResponseDTO;
import com.pedisur.backend.catalog.entity.Category;
import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.tenant.entity.Tenant;

@Component
public class ProductMapper {

    public ProductResponseDTO toResponseDTO(Product product) {
        if (product == null) {
            return null;
        }
        return ProductResponseDTO.builder()
            .id(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .price(product.getPrice())
            .imageUrl(product.getImageUrl())
            .categoryId(product.getCategory().getId())
            .tenantId(product.getTenant().getId())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }

    public Product toEntity(ProductRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return Product.builder()
            .name(dto.getName())
            .description(dto.getDescription())
            .price(dto.getPrice())
            .imageUrl(dto.getImageUrl())
            .category(Category.builder().id(dto.getCategoryId()).build())
            .tenant(Tenant.builder().id(dto.getTenantId()).build())
            .build();
    }
}
