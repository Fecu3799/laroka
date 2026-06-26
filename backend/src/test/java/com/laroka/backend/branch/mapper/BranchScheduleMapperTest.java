package com.laroka.backend.branch.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.laroka.backend.branch.dto.BranchScheduleDayResponseDTO;
import com.laroka.backend.branch.entity.BranchSchedule;
import com.laroka.backend.branch.entity.WeekDay;

class BranchScheduleMapperTest {

	private final BranchScheduleMapper mapper = new BranchScheduleMapper();

	@Test
	void toWeekResponse_emptyInput_returnsSevenInactiveDaysInOrder() {
		List<BranchScheduleDayResponseDTO> result = mapper.toWeekResponse(List.of());

		assertThat(result).hasSize(7);
		assertThat(result).extracting(BranchScheduleDayResponseDTO::getDayOfWeek)
			.containsExactly(WeekDay.MON, WeekDay.TUE, WeekDay.WED, WeekDay.THU,
				WeekDay.FRI, WeekDay.SAT, WeekDay.SUN);
		assertThat(result).allSatisfy(d -> {
			assertThat(d.isActive()).isFalse();
			assertThat(d.getOpenTime()).isNull();
			assertThat(d.getCloseTime()).isNull();
		});
	}

	@Test
	void toWeekResponse_partialInput_fillsMissingDaysAndKeepsExisting() {
		BranchSchedule wed = BranchSchedule.builder()
			.dayOfWeek(WeekDay.WED).active(true)
			.openTime(LocalTime.of(11, 0)).closeTime(LocalTime.of(23, 0))
			.build();

		List<BranchScheduleDayResponseDTO> result = mapper.toWeekResponse(List.of(wed));

		assertThat(result).hasSize(7);
		BranchScheduleDayResponseDTO wedDto = result.get(2); // MON, TUE, WED
		assertThat(wedDto.getDayOfWeek()).isEqualTo(WeekDay.WED);
		assertThat(wedDto.isActive()).isTrue();
		assertThat(wedDto.getOpenTime()).isEqualTo(LocalTime.of(11, 0));
		assertThat(wedDto.getCloseTime()).isEqualTo(LocalTime.of(23, 0));
		// El resto inactivo.
		assertThat(result.get(0).isActive()).isFalse();
	}
}
