package com.laroka.backend.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final CategoryRepository repository;
	private final TenantRepository tenantRepository;

	public Category findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new CategoryNotFoundException(id));
	}

	public List<Category> findByTenant(Integer tenantId) {
		validateTenantExists(tenantId);
		return repository.findByTenantId(tenantId);
	}

	public List<Category> findAll() {
		return repository.findAll();
	}

	public Category create(Category category) {
		Tenant tenant = validateTenantExists(category.getTenant().getId());
		category.setTenant(tenant);
		return repository.save(category);
	}

	public Category update(Integer id, Category updates) {
		Category category = findById(id);
		Tenant tenant = validateTenantExists(updates.getTenant().getId());
		category.setName(updates.getName());
		category.setTenant(tenant);
		return repository.save(category);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}

	private Tenant validateTenantExists(Integer tenantId) {
		return tenantRepository.findById(tenantId)
			.orElseThrow(() -> new TenantNotFoundException(tenantId));
	}
}
