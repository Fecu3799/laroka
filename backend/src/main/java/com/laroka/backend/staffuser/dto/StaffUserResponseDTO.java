package com.laroka.backend.staffuser.dto;

import java.time.LocalDateTime;

import com.laroka.backend.staffuser.entity.UserRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffUserResponseDTO {

	private Integer id;
	private String name;
	private String email;
	private UserRole role;
	private Integer branchId;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
