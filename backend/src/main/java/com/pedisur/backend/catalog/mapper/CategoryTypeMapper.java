package com.pedisur.backend.catalog.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.pedisur.backend.catalog.dto.CategoryTypeResponseDTO;
import com.pedisur.backend.catalog.entity.CategoryType;

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
