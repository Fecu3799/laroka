package com.laroka.backend.shift.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;

public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {

    Optional<WorkShift> findByBranchIdAndStatus(Integer branchId, ShiftStatus status);
}
