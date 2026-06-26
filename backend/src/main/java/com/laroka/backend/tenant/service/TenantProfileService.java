package com.laroka.backend.tenant.service;

import org.springframework.stereotype.Service;

import com.laroka.backend.tenant.entity.TenantProfile;
import com.laroka.backend.tenant.exception.TenantProfileNotFoundException;
import com.laroka.backend.tenant.repository.TenantProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantProfileService {

	private final TenantProfileRepository repository;

	public TenantProfile findByTenantId(Integer tenantId) {
		return repository.findByTenantId(tenantId)
			.orElseThrow(() -> new TenantProfileNotFoundException(tenantId));
	}
}
