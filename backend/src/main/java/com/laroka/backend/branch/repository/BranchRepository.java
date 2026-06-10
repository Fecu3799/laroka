package com.laroka.backend.branch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.branch.entity.Branch;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {
	List<Branch> findByTenantId(Integer tenantId);

	Branch findByNameAndTenantId(String name, Integer tenantId);

	boolean existsByIdAndTenantId(Integer id, Integer tenantId);
}
