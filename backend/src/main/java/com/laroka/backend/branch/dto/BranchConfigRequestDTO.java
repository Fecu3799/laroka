package com.laroka.backend.branch.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
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

	// US-15-02 / US-15-F-01: campos opcionales del patch parcial. Si llegan null se
	// omiten y no se pisan los valores existentes en DB (las columnas son NOT NULL).
	private String name;

	private String address;

	private String phone;

	@DecimalMin(value = "0", message = "deliveryFee must be at least 0")
	private BigDecimal deliveryFee;

	@DecimalMin(value = "0", message = "serviceFee must be at least 0")
	private BigDecimal serviceFee;

	@Min(value = 1, message = "estimatedDeliveryMinutes must be at least 1")
	private Integer estimatedDeliveryMinutes;
}
