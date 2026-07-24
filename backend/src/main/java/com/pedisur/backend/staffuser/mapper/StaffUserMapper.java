package com.pedisur.backend.staffuser.mapper;

import org.springframework.stereotype.Component;

import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.staffuser.dto.StaffUserRequestDTO;
import com.pedisur.backend.staffuser.dto.StaffUserResponseDTO;
import com.pedisur.backend.staffuser.dto.StaffUserUpdateRequestDTO;
import com.pedisur.backend.staffuser.entity.StaffUser;

@Component
public class StaffUserMapper {

	public StaffUserResponseDTO toResponseDTO(StaffUser user) {
		if (user == null) {
			return null;
		}
		return StaffUserResponseDTO.builder()
			.id(user.getId())
			.name(user.getName())
			.email(user.getEmail())
			.role(user.getRole())
			.branchId(user.getBranch().getId())
			.branchName(user.getBranch().getName())
			.active(user.isActive())
			.createdAt(user.getCreatedAt())
			.updatedAt(user.getUpdatedAt())
			.build();
	}

	public StaffUser toEntity(StaffUserRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return StaffUser.builder()
			.name(dto.getName())
			.passwordHash(dto.getPassword())
			.role(dto.getRole())
			.branch(Branch.builder().id(dto.getBranchId()).build())
			.build();
	}

	public StaffUser toUpdateEntity(StaffUserUpdateRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return StaffUser.builder()
			.name(dto.getName())
			.role(dto.getRole())
			.branch(Branch.builder().id(dto.getBranchId()).build())
			.build();
	}
}
