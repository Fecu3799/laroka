package com.laroka.backend.catalog.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.exception.BranchProductNotFoundException;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.exception.ProductNotFoundException;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository repository;
	private final CategoryRepository categoryRepository;
	private final BranchRepository branchRepository;
	private final BranchProductRepository branchProductRepository;
	private final TenantRepository tenantRepository;

	public Product findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new ProductNotFoundException(id));
	}

	@Cacheable(value = "menu", key = "#branchId")
	public List<BranchProduct> getMenuForBranch(Integer branchId) {
		validateBranchExists(branchId);
		return branchProductRepository.findByBranchIdAndAvailableTrue(branchId);
	}

	public List<Product> findByCategory(Integer categoryId) {
		validateCategoryExists(categoryId);
		return repository.findByCategoryId(categoryId);
	}

	public List<Product> findByTenant(Integer tenantId) {
		validateTenantExists(tenantId);
		return repository.findByTenantId(tenantId);
	}

	public List<Product> findAll() {
		return repository.findAll();
	}

	public Product create(Product product) {
		Category category = validateCategoryExists(product.getCategory().getId());
		Tenant tenant = validateTenantExists(product.getTenant().getId());
		product.setCategory(category);
		product.setTenant(tenant);
		return repository.save(product);
	}

	public Product update(Integer id, Product updates) {
		Product product = findById(id);
		Category category = validateCategoryExists(updates.getCategory().getId());
		Tenant tenant = validateTenantExists(updates.getTenant().getId());
		product.setName(updates.getName());
		product.setDescription(updates.getDescription());
		product.setPrice(updates.getPrice());
		product.setImageUrl(updates.getImageUrl());
		product.setCategory(category);
		product.setTenant(tenant);
		return repository.save(product);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}

	@CacheEvict(value = "menu", key = "#branchId")
	public Product updateAvailability(Integer productId, Boolean available, Integer branchId) {
		if (branchId == null) {
			throw new BusinessException("Branch ID is required to update product availability");
		}
		BranchProduct branchProduct = branchProductRepository.findByBranchIdAndProductId(branchId, productId)
			.orElseThrow(() -> new BranchProductNotFoundException(branchId, productId));
		branchProduct.setAvailable(available);
		branchProductRepository.save(branchProduct);
		return branchProduct.getProduct();
	}

	private Category validateCategoryExists(Integer categoryId) {
		return categoryRepository.findById(categoryId)
			.orElseThrow(() -> new CategoryNotFoundException(categoryId));
	}

	private void validateBranchExists(Integer branchId) {
		branchRepository.findById(branchId)
			.orElseThrow(() -> new BranchNotFoundException(branchId));
	}

	private Tenant validateTenantExists(Integer tenantId) {
		return tenantRepository.findById(tenantId)
			.orElseThrow(() -> new TenantNotFoundException(tenantId));
	}
}
