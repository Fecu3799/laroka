package com.pedisur.backend.branch.mapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.pedisur.backend.branch.dto.BranchScheduleDayRequestDTO;
import com.pedisur.backend.branch.dto.BranchScheduleDayResponseDTO;
import com.pedisur.backend.branch.entity.BranchSchedule;
import com.pedisur.backend.branch.entity.WeekDay;

@Component
public class BranchScheduleMapper {

	/**
	 * Convierte el DTO de entrada a una entidad "carrier" (sin branch). El service
	 * resuelve la asociación con la sucursal y el upsert.
	 */
	public BranchSchedule toEntity(BranchScheduleDayRequestDTO dto) {
		if (dto == null) {
			return null;
		}
		return BranchSchedule.builder()
			.dayOfWeek(dto.getDayOfWeek())
			.active(dto.isActive())
			.openTime(dto.getOpenTime())
			.closeTime(dto.getCloseTime())
			.openTime2(dto.getOpenTime2())
			.closeTime2(dto.getCloseTime2())
			.build();
	}

	/**
	 * Devuelve siempre 7 entradas, una por día de la semana en orden MON..SUN.
	 * Los días sin registro en branch_schedule se devuelven con active=false y
	 * horarios null.
	 */
	public List<BranchScheduleDayResponseDTO> toWeekResponse(List<BranchSchedule> existing) {
		Map<WeekDay, BranchSchedule> byDay = existing.stream()
			.collect(Collectors.toMap(BranchSchedule::getDayOfWeek, Function.identity()));

		return Arrays.stream(WeekDay.values())
			.map(day -> {
				BranchSchedule s = byDay.get(day);
				if (s == null) {
					return BranchScheduleDayResponseDTO.builder()
						.dayOfWeek(day)
						.active(false)
						.build();
				}
				return BranchScheduleDayResponseDTO.builder()
					.dayOfWeek(day)
					.active(s.isActive())
					.openTime(s.getOpenTime())
					.closeTime(s.getCloseTime())
					.openTime2(s.getOpenTime2())
					.closeTime2(s.getCloseTime2())
					.build();
			})
			.toList();
	}
}
