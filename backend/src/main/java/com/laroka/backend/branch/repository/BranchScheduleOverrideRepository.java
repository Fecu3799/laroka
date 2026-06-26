package com.laroka.backend.branch.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.branch.entity.BranchScheduleOverride;

@Repository
public interface BranchScheduleOverrideRepository extends JpaRepository<BranchScheduleOverride, Integer> {
	Optional<BranchScheduleOverride> findByBranchIdAndDate(Integer branchId, LocalDate date);
}
