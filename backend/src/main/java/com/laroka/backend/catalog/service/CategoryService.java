package com.laroka.backend.catalog.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.catalog.repository.ProductRepository.CategoryProductCount;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final CategoryRepository repository;
	private final ProductRepository productRepository;
	private final BranchProductRepository branchProductRepository;
	private final TenantRepository tenantRepository;

	public Category findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new CategoryNotFoundException(id));
	}

	public List<Category> findByTenant(Integer tenantId) {
		validateTenantExists(tenantId);
		return repository.findByTenantIdOrderByNameAsc(tenantId);
	}

	public List<Category> findAll() {
		return repository.findAllByOrderByNameAsc();
	}

	// US-14-05: cantidad de productos por categoría, keyed por categoryId. Las categorías
	// sin productos no aparecen en el mapa (el mapper resuelve a 0).
	public Map<Integer, Long> countProductsByCategory() {
		return productRepository.countGroupedByCategory().stream()
			.collect(Collectors.toMap(CategoryProductCount::getCategoryId, CategoryProductCount::getCount));
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

	// Eliminar una categoría borra en cascada sus productos y, por cada producto, sus
	// entradas branch_product (FK), todo en la misma transacción. El frontend confirma la
	// acción mostrando la cantidad de productos afectados. Invalida el caché del menú de
	// todas las sucursales porque esos productos dejan de existir.
	@Transactional
	@CacheEvict(value = "menu", allEntries = true)
	public void delete(Integer id) {
		Category category = findById(id);
		List<Product> products = productRepository.findByCategoryId(id);
		for (Product product : products) {
			branchProductRepository.deleteByProductId(product.getId());
		}
		productRepository.deleteAll(products);
		repository.delete(category);
	}

	private Tenant validateTenantExists(Integer tenantId) {
		return tenantRepository.findById(tenantId)
			.orElseThrow(() -> new TenantNotFoundException(tenantId));
	}
}
