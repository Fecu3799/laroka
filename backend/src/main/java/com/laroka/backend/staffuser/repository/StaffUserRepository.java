package com.laroka.backend.staffuser.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.staffuser.entity.StaffUser;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, Integer> {

	@Query("SELECT u.active FROM StaffUser u WHERE u.id = :id")
	Optional<Boolean> findActiveById(@Param("id") Integer id);

	Optional<StaffUser> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Integer id);

	@Query("SELECT u FROM StaffUser u JOIN FETCH u.branch b JOIN FETCH b.tenant WHERE u.email = :email")
	Optional<StaffUser> findByEmailWithBranchAndTenant(@Param("email") String email);

	@Query("SELECT u FROM StaffUser u JOIN FETCH u.branch b JOIN FETCH b.tenant WHERE u.id = :id")
	Optional<StaffUser> findByIdWithBranchAndTenant(@Param("id") Integer id);

	@Query("SELECT u FROM StaffUser u JOIN FETCH u.branch b JOIN FETCH b.tenant t WHERE t.id = :tenantId")
	List<StaffUser> findAllByTenantId(@Param("tenantId") Integer tenantId);
}
