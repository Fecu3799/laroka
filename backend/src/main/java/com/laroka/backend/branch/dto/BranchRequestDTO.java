package com.laroka.backend.branch.dto;

import jakarta.validation.constraints.Min;
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
public class BranchRequestDTO {
	@NotBlank(message = "Branch name is required")
	private String name;

	@NotBlank(message = "Branch address is required")
	private String address;

	@NotNull(message = "Tenant ID is required")
	private Integer tenantId;

	@NotNull(message = "Estimated delivery minutes is required")
	@Min(value = 1, message = "Estimated delivery minutes must be at least 1")
	private Integer estimatedDeliveryMinutes;

	@NotBlank(message = "Phone is required")
	private String phone;
}
