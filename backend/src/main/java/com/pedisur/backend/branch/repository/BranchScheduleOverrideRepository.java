package com.pedisur.backend.branch.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.branch.entity.BranchScheduleOverride;

@Repository
public interface BranchScheduleOverrideRepository extends JpaRepository<BranchScheduleOverride, Integer> {
	Optional<BranchScheduleOverride> findByBranchIdAndDate(Integer branchId, LocalDate date);
}
