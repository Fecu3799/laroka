package com.laroka.backend.branch.dto;

import java.math.BigDecimal;

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
}
