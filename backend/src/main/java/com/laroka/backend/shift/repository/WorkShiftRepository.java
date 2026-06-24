package com.laroka.backend.shift.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;

public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {

    Optional<WorkShift> findByBranchIdAndStatus(Integer branchId, ShiftStatus status);

    @Query("SELECT s FROM WorkShift s JOIN FETCH s.branch WHERE s.status = :status")
    List<WorkShift> findAllByStatusWithBranch(@Param("status") ShiftStatus status);

    @EntityGraph(attributePaths = {"openedBy"})
    @Query("SELECT s FROM WorkShift s WHERE s.branch.id = :branchId AND s.status = :status")
    Optional<WorkShift> findByBranchIdAndStatusWithOpenedBy(
            @Param("branchId") Integer branchId,
            @Param("status") ShiftStatus status);

    @EntityGraph(attributePaths = {"openedBy", "closedBy", "summary"})
    Page<WorkShift> findByBranchIdAndStatus(Integer branchId, ShiftStatus status, Pageable pageable);
}
