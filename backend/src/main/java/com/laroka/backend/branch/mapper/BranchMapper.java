package com.laroka.backend.branch.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.branch.dto.BranchPublicDTO;
import com.laroka.backend.branch.dto.BranchRequestDTO;
import com.laroka.backend.branch.dto.BranchResponseDTO;
import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.mapper.PizzeriaMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BranchMapper {

	private final PizzeriaMapper pizzeriaMapper;

	public BranchResponseDTO toResponseDTO(Branch branch) {
		if (branch == null) {
			return null;
		}
		return BranchResponseDTO.builder()
			.id(branch.getId())
			.name(branch.getName())
			.address(branch.getAddress())
			.estimatedDeliveryMinutes(branch.getEstimatedDeliveryMinutes())
			.phone(branch.getPhone())
			.pizzeriaId(branch.getPizzeria().getId())
			.pizzeria(pizzeriaMapper.toResponseDTO(branch.getPizzeria()))
			.createdAt(branch.getCreatedAt())
			.updatedAt(branch.getUpdatedAt())
			.build();
	}

	public BranchPublicDTO toPublicDTO(Branch branch) {
		if (branch == null) {
			return null;
		}
		return BranchPublicDTO.builder()
			.id(branch.getId())
			.name(branch.getName())
			.address(branch.getAddress())
			.deliveryFee(branch.getDeliveryFee())
			.serviceFee(branch.getServiceFee())
			.estimatedDeliveryMinutes(branch.getEstimatedDeliveryMinutes())
			.phone(branch.getPhone())
			.build();
	}

	public Branch toEntity(BranchRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return Branch.builder()
			.name(dto.getName())
			.address(dto.getAddress())
			.estimatedDeliveryMinutes(dto.getEstimatedDeliveryMinutes())
			.phone(dto.getPhone())
			.pizzeria(Pizzeria.builder().id(dto.getPizzeriaId()).build())
			.build();
	}
}
