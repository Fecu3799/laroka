package com.laroka.backend.branch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.branch.entity.BranchQR;

@Repository
public interface BranchQRRepository extends JpaRepository<BranchQR, Integer> {
	Optional<BranchQR> findByBranchId(Integer branchId);
}
