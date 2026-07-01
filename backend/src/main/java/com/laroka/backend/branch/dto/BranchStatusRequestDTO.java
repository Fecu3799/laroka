package com.laroka.backend.branch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchStatusRequestDTO {

	@NotNull(message = "active is required")
	private Boolean active;
}
