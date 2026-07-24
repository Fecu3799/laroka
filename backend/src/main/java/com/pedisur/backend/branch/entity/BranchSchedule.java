package com.pedisur.backend.branch.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "branch_schedule")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchSchedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 3)
	private WeekDay dayOfWeek;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "open_time")
	private LocalTime openTime;

	@Column(name = "close_time")
	private LocalTime closeTime;

	@Column(name = "open_time_2")
	private LocalTime openTime2;

	@Column(name = "close_time_2")
	private LocalTime closeTime2;
}
