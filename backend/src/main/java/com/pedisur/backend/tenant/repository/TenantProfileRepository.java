package com.pedisur.backend.tenant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.tenant.entity.TenantProfile;

@Repository
public interface TenantProfileRepository extends JpaRepository<TenantProfile, Integer> {
	Optional<TenantProfile> findByTenantId(Integer tenantId);
}
