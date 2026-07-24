package com.pedisur.backend.tenant.service;

import org.springframework.stereotype.Service;

import com.pedisur.backend.tenant.entity.TenantProfile;
import com.pedisur.backend.tenant.exception.TenantNotFoundException;
import com.pedisur.backend.tenant.exception.TenantProfileNotFoundException;
import com.pedisur.backend.tenant.repository.TenantProfileRepository;
import com.pedisur.backend.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantProfileService {

	private final TenantProfileRepository repository;
	private final TenantRepository tenantRepository;

	public TenantProfile findByTenantId(Integer tenantId) {
		return repository.findByTenantId(tenantId)
			.orElseThrow(() -> new TenantProfileNotFoundException(tenantId));
	}

	/**
	 * Crea el perfil del tenant si no existe, o actualiza el existente (upsert).
	 * El tenantId proviene del ADMIN autenticado, no del body.
	 */
	public TenantProfile upsert(Integer tenantId, TenantProfile data) {
		TenantProfile profile = repository.findByTenantId(tenantId)
			.orElseGet(() -> {
				TenantProfile created = new TenantProfile();
				created.setTenant(tenantRepository.findById(tenantId)
					.orElseThrow(() -> new TenantNotFoundException(tenantId)));
				return created;
			});

		profile.setBusinessName(data.getBusinessName());
		profile.setDescription(data.getDescription());
		profile.setInstagramUrl(data.getInstagramUrl());
		profile.setFacebookUrl(data.getFacebookUrl());
		profile.setWhatsapp(data.getWhatsapp());
		profile.setLogoUrl(data.getLogoUrl());

		return repository.save(profile);
	}
}
