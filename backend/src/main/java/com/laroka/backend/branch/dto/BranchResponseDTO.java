package com.laroka.backend.branch.dto;

import java.time.LocalDateTime;

import com.laroka.backend.pizzeria.dto.PizzeriaResponseDTO;

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
	private Integer estimatedDeliveryMinutes;
	private String phone;
	private Integer pizzeriaId;
	private PizzeriaResponseDTO pizzeria;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
