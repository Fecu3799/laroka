package com.laroka.backend.catalog.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.CategoryType;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.exception.CategoryTypeNotFoundException;
import com.laroka.backend.catalog.event.MenuCacheEvictionEvent;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.catalog.repository.CategoryTypeRepository;
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
	private final CategoryTypeRepository categoryTypeRepository;
	private final ApplicationEventPublisher eventPublisher;

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
		category.setCategoryType(validateCategoryTypeExists(category.getCategoryType().getId()));
		return repository.save(category);
	}

	// name y categoryType viajan al menú cacheado: MenuMapper expone categoryName y deriva
	// allowsHalfAndHalf/allowsSizes del tipo. Sin evict, renombrar una categoría o cambiarle
	// el tipo no se refleja hasta que expire el TTL — y un cambio de allowsSizes decide si el
	// client muestra o no el selector de tamaños. El evict es total porque la categoría
	// aparece en el menú de todas las sucursales del tenant.
	//
	// @CacheEvict directo (sin evento) alcanza: este método hace una sola escritura, así que
	// la transacción propia de save() ya lo hace atómico y no hace falta @Transactional. Sin
	// @Transactional no hay orden indeterminado entre advisors: la evicción corre después de
	// que save() commiteó. Mismo criterio que ProductService.update.
	//
	// create() NO evicta a propósito: una categoría recién creada no tiene productos, y
	// MenuMapper arma el menú agrupando branch_product, así que no puede aparecer en él.
	@CacheEvict(value = "menu", allEntries = true)
	public Category update(Integer id, Category updates) {
		Category category = findById(id);
		Tenant tenant = validateTenantExists(updates.getTenant().getId());
		category.setName(updates.getName());
		category.setTenant(tenant);
		category.setCategoryType(validateCategoryTypeExists(updates.getCategoryType().getId()));
		return repository.save(category);
	}

	// Eliminar una categoría borra en cascada sus productos y, por cada producto, sus
	// entradas branch_product (FK), todo en la misma transacción. El frontend confirma la
	// acción mostrando la cantidad de productos afectados. Invalida el caché del menú de
	// todas las sucursales porque esos productos dejan de existir.
	//
	// La evicción NO va con @CacheEvict: el orden entre el advisor de transacción y el de
	// cache no está garantizado, y evictar antes del commit abre una ventana donde un request
	// concurrente repuebla "menu" con productos todavía vivos. Se publica el evento y
	// MenuCacheEvictionListener evicta en AFTER_COMMIT (mismo patrón que ProductService.delete).
	@Transactional
	public void delete(Integer id) {
		Category category = findById(id);
		List<Product> products = productRepository.findByCategoryId(id);
		for (Product product : products) {
			branchProductRepository.deleteByProductId(product.getId());
		}
		productRepository.deleteAll(products);
		repository.delete(category);
		eventPublisher.publishEvent(MenuCacheEvictionEvent.categoryDeleted(id));
	}

	private Tenant validateTenantExists(Integer tenantId) {
		return tenantRepository.findById(tenantId)
			.orElseThrow(() -> new TenantNotFoundException(tenantId));
	}

	// US-CAT-03: resuelve la entidad gestionada del tipo maestro para persistir la FK y para
	// que el mapper pueda exponer su name sin tocar un proxy lazy.
	private CategoryType validateCategoryTypeExists(Integer categoryTypeId) {
		return categoryTypeRepository.findById(categoryTypeId)
			.orElseThrow(() -> new CategoryTypeNotFoundException(categoryTypeId));
	}
}
