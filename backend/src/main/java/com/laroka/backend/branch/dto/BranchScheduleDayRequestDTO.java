package com.laroka.backend.branch.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laroka.backend.branch.entity.WeekDay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchScheduleDayRequestDTO {

	private WeekDay dayOfWeek;
	private boolean active;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime openTime;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime closeTime;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime openTime2;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime closeTime2;
}
