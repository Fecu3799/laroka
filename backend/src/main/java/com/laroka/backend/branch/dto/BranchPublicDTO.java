package com.laroka.backend.branch.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchPublicDTO {
	private Integer id;
	private String name;
	private String address;
	private BigDecimal deliveryFee;
	private BigDecimal serviceFee;
	private Integer estimatedDeliveryMinutes;
	private String phone;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime openingTime;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime closingTime;

	private String openDays;
}
