package com.laroka.backend.catalog.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.ProductRequestDTO;
import com.laroka.backend.catalog.dto.ProductResponseDTO;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.pizzeria.entity.Pizzeria;

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
            .available(product.getAvailable())
            .categoryId(product.getCategory().getId())
            .pizzeriaId(product.getPizzeria().getId())
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
            .available(dto.getAvailable())
            .category(Category.builder().id(dto.getCategoryId()).build())
            .pizzeria(Pizzeria.builder().id(dto.getPizzeriaId()).build())
            .build();
    }
}
