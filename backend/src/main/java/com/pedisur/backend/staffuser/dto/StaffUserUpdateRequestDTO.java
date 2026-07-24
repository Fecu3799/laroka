package com.pedisur.backend.staffuser.dto;

import com.pedisur.backend.staffuser.entity.UserRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffUserUpdateRequestDTO {

	@NotBlank(message = "Name is required")
	private String name;

	@NotNull(message = "Role is required")
	private UserRole role;

	@NotNull(message = "Branch ID is required")
	private Integer branchId;
}
