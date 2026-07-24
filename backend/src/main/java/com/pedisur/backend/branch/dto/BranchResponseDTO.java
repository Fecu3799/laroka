package com.pedisur.backend.branch.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.pedisur.backend.tenant.dto.TenantResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchResponseDTO {
	private Integer id;
	private String name;
	private String address;
	private BigDecimal deliveryFee;
	private BigDecimal serviceFee;
	private Integer estimatedDeliveryMinutes;
	private String phone;
	private String imageUrl;
	private boolean active;
	private Integer maxShiftDurationMinutes;
	private Integer tenantId;
	private TenantResponseDTO tenant;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
