package com.laroka.backend.staffuser.mapper;

import org.springframework.stereotype.Component;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.staffuser.dto.StaffUserRequestDTO;
import com.laroka.backend.staffuser.dto.StaffUserResponseDTO;
import com.laroka.backend.staffuser.entity.StaffUser;

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
			.active(true)
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
}
