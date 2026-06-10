package com.laroka.backend.shift.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.laroka.backend.shift.entity.WorkShiftSummary;

public interface WorkShiftSummaryRepository extends JpaRepository<WorkShiftSummary, UUID> {

    Optional<WorkShiftSummary> findByShiftId(UUID shiftId);
}
