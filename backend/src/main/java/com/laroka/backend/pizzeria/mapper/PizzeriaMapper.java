package com.laroka.backend.pizzeria.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.pizzeria.dto.PizzeriaRequestDTO;
import com.laroka.backend.pizzeria.dto.PizzeriaResponseDTO;
import com.laroka.backend.pizzeria.entity.Pizzeria;

@Component
public class PizzeriaMapper {

	public PizzeriaResponseDTO toResponseDTO(Pizzeria pizzeria) {
		if (pizzeria == null) {
			return null;
		}
		return PizzeriaResponseDTO.builder()
			.id(pizzeria.getId())
			.name(pizzeria.getName())
			.createdAt(pizzeria.getCreatedAt())
			.updatedAt(pizzeria.getUpdatedAt())
			.build();
	}

	public Pizzeria toEntity(PizzeriaRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return Pizzeria.builder()
			.name(dto.getName())
			.build();
	}
}
