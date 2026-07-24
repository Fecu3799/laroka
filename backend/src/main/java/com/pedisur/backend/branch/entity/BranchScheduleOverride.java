package com.pedisur.backend.branch.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "branch_schedule_override")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchScheduleOverride {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(name = "date", nullable = false)
	private LocalDate date;

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

	@Column(name = "reason")
	private String reason;

	@Builder.Default
	@Column(name = "priority", nullable = false)
	private Integer priority = 0;
}
