package com.pedisur.backend.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.tenant.entity.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Integer> {
	Tenant findByName(String name);
}
