package com.pedisur.backend.branch.mapper;

import org.springframework.stereotype.Component;

import com.pedisur.backend.branch.dto.BranchPublicDTO;
import com.pedisur.backend.branch.dto.BranchRequestDTO;
import com.pedisur.backend.branch.dto.BranchResponseDTO;
import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.branch.repository.BranchScheduleRepository;
import com.pedisur.backend.tenant.entity.Tenant;
import com.pedisur.backend.tenant.mapper.TenantMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BranchMapper {

	private final TenantMapper tenantMapper;
	private final BranchScheduleRepository branchScheduleRepository;
	private final BranchScheduleMapper branchScheduleMapper;

	public BranchResponseDTO toResponseDTO(Branch branch) {
		if (branch == null) {
			return null;
		}
		return BranchResponseDTO.builder()
			.id(branch.getId())
			.name(branch.getName())
			.address(branch.getAddress())
			.deliveryFee(branch.getDeliveryFee())
			.serviceFee(branch.getServiceFee())
			.estimatedDeliveryMinutes(branch.getEstimatedDeliveryMinutes())
			.phone(branch.getPhone())
			.imageUrl(branch.getImageUrl())
			.active(branch.isActive())
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
			.imageUrl(branch.getImageUrl())
			.acceptingOrders(branch.isAcceptingOrders())
			.schedule(branchScheduleMapper.toWeekResponse(
				branchScheduleRepository.findByBranchId(branch.getId())))
			.build();
	}

	public Branch toEntity(BranchRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return Branch.builder()
			.name(dto.getName())
			.address(dto.getAddress())
			.deliveryFee(dto.getDeliveryFee())
			.serviceFee(dto.getServiceFee())
			.estimatedDeliveryMinutes(dto.getEstimatedDeliveryMinutes())
			.phone(dto.getPhone())
			.imageUrl(dto.getImageUrl())
			.tenant(Tenant.builder().id(dto.getTenantId()).build())
			.build();
	}
}
