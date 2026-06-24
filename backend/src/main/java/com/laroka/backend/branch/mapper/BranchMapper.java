package com.laroka.backend.branch.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.branch.dto.BranchPublicDTO;
import com.laroka.backend.branch.dto.BranchRequestDTO;
import com.laroka.backend.branch.dto.BranchResponseDTO;
import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.mapper.TenantMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BranchMapper {

	private final TenantMapper tenantMapper;

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
			.maxShiftDurationMinutes(branch.getMaxShiftDurationMinutes())
			.tenantId(branch.getTenant().getId())
			.tenant(tenantMapper.toResponseDTO(branch.getTenant()))
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
			.openingTime(branch.getOpeningTime())
			.closingTime(branch.getClosingTime())
			.openDays(branch.getOpenDays())
			.acceptingOrders(branch.isAcceptingOrders())
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
			.tenant(Tenant.builder().id(dto.getTenantId()).build())
			.build();
	}
}
