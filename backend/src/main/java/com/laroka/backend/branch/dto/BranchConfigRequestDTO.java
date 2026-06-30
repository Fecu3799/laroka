package com.laroka.backend.branch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchConfigRequestDTO {

	@NotNull(message = "maxShiftDurationMinutes is required")
	@Min(value = 1, message = "maxShiftDurationMinutes must be at least 1")
	private Integer maxShiftDurationMinutes;

	// US-15-02: campos opcionales del patch parcial. Si llegan null se omiten y
	// no se pisan los valores existentes en DB.
	private String address;

	private String phone;
}
