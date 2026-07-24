package com.pedisur.backend.shift.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pedisur.backend.shift.entity.WorkShiftSummary;

public interface WorkShiftSummaryRepository extends JpaRepository<WorkShiftSummary, UUID> {

    Optional<WorkShiftSummary> findByShiftId(UUID shiftId);
}
