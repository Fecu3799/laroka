package com.laroka.backend.catalog.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.laroka.backend.catalog.dto.CategoryTypeResponseDTO;
import com.laroka.backend.catalog.entity.CategoryType;

@Component
public class CategoryTypeMapper {

	public CategoryTypeResponseDTO toResponseDTO(CategoryType type) {
		if (type == null) {
			return null;
		}
		return CategoryTypeResponseDTO.builder()
			.id(type.getId())
			.name(type.getName())
			.allowsHalfAndHalf(type.isAllowsHalfAndHalf())
			.allowsSizes(type.isAllowsSizes())
			.build();
	}

	public List<CategoryTypeResponseDTO> toResponseDTOList(List<CategoryType> types) {
		return types.stream().map(this::toResponseDTO).toList();
	}
}
