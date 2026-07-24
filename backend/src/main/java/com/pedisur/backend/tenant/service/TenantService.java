package com.pedisur.backend.tenant.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.pedisur.backend.tenant.entity.Tenant;
import com.pedisur.backend.tenant.exception.TenantNotFoundException;
import com.pedisur.backend.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantService {

	private final TenantRepository repository;

	public Tenant findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new TenantNotFoundException(id));
	}

	public List<Tenant> findAll() {
		return repository.findAll();
	}

	public Tenant create(Tenant tenant) {
		return repository.save(tenant);
	}

	public Tenant update(Integer id, Tenant updates) {
		Tenant tenant = findById(id);
		tenant.setName(updates.getName());
		return repository.save(tenant);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}
}
