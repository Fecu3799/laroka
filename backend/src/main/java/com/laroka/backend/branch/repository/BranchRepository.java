package com.laroka.backend.branch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.branch.entity.Branch;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {
	List<Branch> findByTenantId(Integer tenantId);

	Branch findByNameAndTenantId(String name, Integer tenantId);

	boolean existsByIdAndTenantId(Integer id, Integer tenantId);

	@Modifying
	@Query("UPDATE Branch b SET b.acceptingOrders = :value WHERE b.id = :branchId")
	int updateAcceptingOrders(@Param("branchId") Integer branchId, @Param("value") boolean value);
}
