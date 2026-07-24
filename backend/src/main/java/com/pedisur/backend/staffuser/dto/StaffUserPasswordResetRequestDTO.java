package com.pedisur.backend.staffuser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffUserPasswordResetRequestDTO {

	@NotBlank(message = "New password is required")
	@Size(min = 8, message = "Password must be at least 8 characters")
	private String newPassword;
}
