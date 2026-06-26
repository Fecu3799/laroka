package com.laroka.backend.branch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.branch.entity.BranchSchedule;
import com.laroka.backend.branch.entity.WeekDay;

@Repository
public interface BranchScheduleRepository extends JpaRepository<BranchSchedule, Integer> {
	Optional<BranchSchedule> findByBranchIdAndDayOfWeek(Integer branchId, WeekDay dayOfWeek);

	List<BranchSchedule> findByBranchId(Integer branchId);
}
