package com.laroka.backend.branch.entity;

import java.time.DayOfWeek;

/**
 * Día de la semana del modelo de horarios (branch_schedule.day_of_week, VARCHAR(3)).
 */
public enum WeekDay {
	MON, TUE, WED, THU, FRI, SAT, SUN;

	public static WeekDay from(DayOfWeek dayOfWeek) {
		return switch (dayOfWeek) {
			case MONDAY -> MON;
			case TUESDAY -> TUE;
			case WEDNESDAY -> WED;
			case THURSDAY -> THU;
			case FRIDAY -> FRI;
			case SATURDAY -> SAT;
			case SUNDAY -> SUN;
		};
	}
}
