package com.laroka.backend.catalog.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.CategoryRequestDTO;
import com.laroka.backend.catalog.dto.CategoryResponseDTO;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.mapper.PizzeriaMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CategoryMapper {

	private final PizzeriaMapper pizzeriaMapper;

	public CategoryResponseDTO toResponseDTO(Category category) {
		if (category == null) {
			return null;
		}
		return CategoryResponseDTO.builder()
			.id(category.getId())
			.name(category.getName())
			.pizzeriaId(category.getPizzeria().getId())
			.pizzeria(pizzeriaMapper.toResponseDTO(category.getPizzeria()))
			.createdAt(category.getCreatedAt())
			.updatedAt(category.getUpdatedAt())
			.build();
	}

	public Category toEntity(CategoryRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return Category.builder()
			.name(dto.getName())
			.pizzeria(Pizzeria.builder().id(dto.getPizzeriaId()).build())
			.build();
	}
}
