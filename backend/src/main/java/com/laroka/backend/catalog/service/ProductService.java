package com.laroka.backend.catalog.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.event.ProductDeletedEvent;
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
	private final ProductSizeService productSizeService;
	private final TenantRepository tenantRepository;
	private final ApplicationEventPublisher eventPublisher;

	public Product findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new ProductNotFoundException(id));
	}

	@Cacheable(value = "menu", key = "#branchId")
	public BranchMenu getMenuForBranch(Integer branchId) {
		validateBranchExists(branchId);
		// US-15-11: el menú retorna todos los productos de la sucursal (disponibles y no).
		// El campo available viaja en el DTO; el mapper ordena disponibles primero.
		// US-SIZE-F-02: los tamaños con precio ya resuelto por sucursal viajan en el mismo
		// valor cacheado, para no duplicar los seis puntos de evicción de este cache.
		return new BranchMenu(
			branchProductRepository.findByBranchIdWithProductAndCategory(branchId),
			productSizeService.resolveSizesForBranch(branchId));
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

	// @Transactional: el producto y sus branch_product se persisten atómicamente. Si
	// falla la generación de branch_product, se hace rollback también del producto en
	// vez de dejar un producto sin sus branch_product correspondientes.
	@Transactional
	public Product create(Product product) {
		Category category = validateCategoryExists(product.getCategory().getId());
		Tenant tenant = validateTenantExists(product.getTenant().getId());
		product.setCategory(category);
		product.setTenant(tenant);
		Product saved = repository.save(product);
		// US-14-04: alta automática de un BranchProduct por cada sucursal del tenant,
		// disponible y sin override. Idempotente: no duplica si ya existe la combinación.
		createBranchProductsForTenant(saved);
		return saved;
	}

	private void createBranchProductsForTenant(Product product) {
		List<Branch> branches = branchRepository.findByTenantId(product.getTenant().getId());
		for (Branch branch : branches) {
			if (branchProductRepository.existsByBranchIdAndProductId(branch.getId(), product.getId())) {
				continue;
			}
			branchProductRepository.save(BranchProduct.builder()
				.branch(branch)
				.product(product)
				.available(true)
				.priceOverride(null)
				.build());
		}
	}

	// Un update de producto no está scopeado a una sucursal: cambia atributos del Product
	// (name, description, price, imageUrl, category) que se reflejan en el menú de TODAS las
	// sucursales del tenant. Por eso el evict es total (allEntries=true), mismo criterio que
	// updatePrice y delete. Sin @Transactional (igual que updatePrice): con @CacheEvict solo,
	// la evicción corre después del save y no hay riesgo de repoblar el cache antes del commit.
	@CacheEvict(value = "menu", allEntries = true)
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

	// Eliminar un producto borra primero todas sus entradas branch_product (FK) y luego
	// el producto, todo en la misma transacción. Invalida el caché del menú de todas las
	// sucursales porque el producto deja de existir en cualquiera de ellas.
	//
	// La evicción NO va con @CacheEvict acá: el orden entre el advisor de transacción y el
	// de cache no está garantizado, y evictar antes del commit abre una ventana donde un
	// request concurrente repuebla "menu" con el producto todavía vivo. En su lugar se
	// publica ProductDeletedEvent y MenuCacheEvictionListener evicta en AFTER_COMMIT.
	@Transactional
	public void delete(Integer id) {
		Product product = findById(id);
		branchProductRepository.deleteByProductId(id);
		repository.delete(product);
		eventPublisher.publishEvent(new ProductDeletedEvent(id));
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

	// US-14-03: configuración por sucursal de un producto. Una entrada por cada sucursal
	// del tenant (cada sucursal tiene su BranchProduct, garantizado por US-14-04), con
	// branch y product cargados para resolver branchName y precio efectivo en el mapper.
	public ProductBranchConfig getBranchProductConfig(Integer productId) {
		findById(productId);
		// US-15-06: se excluyen las sucursales inactivas de la config por sucursal. El
		// BranchProduct NO se borra ni modifica: sigue en DB con su priceOverride/available;
		// al reactivar la sucursal reaparece con esos mismos valores (solo se filtra al leer).
		List<BranchProduct> rows = branchProductRepository.findConfigByProductId(productId).stream()
			.filter(bp -> bp.getBranch().isActive())
			.toList();

		// US-SIZE-F-01: el tamaño y sus overrides viajan en la misma respuesta para que el
		// backoffice pueda mostrar el precio del tamaño por sucursal sin una segunda llamada.
		ProductSize activeSize = productSizeService.findActiveByProduct(productId).orElse(null);
		Map<Integer, BigDecimal> sizeOverrides = activeSize == null
			? Map.of()
			: productSizeService.findBranchOverrides(activeSize.getId());

		return new ProductBranchConfig(rows, activeSize, sizeOverrides);
	}

	@CacheEvict(value = "menu", key = "#branchId")
	public Product updateBranchConfig(Integer productId, Integer branchId, BigDecimal priceOverride,
			Boolean available) {
		if (branchId == null) {
			throw new BusinessException("Branch ID is required to update branch product config");
		}
		// US-15-06: guard de escritura. No se permite modificar la config de un producto
		// para una sucursal desactivada (el GET ya la excluye; esto cierra el acceso directo
		// vía API). La branch se carga aparte porque bp.getBranch() es lazy y open-in-view=false.
		Branch branch = branchRepository.findById(branchId)
			.orElseThrow(() -> new BranchNotFoundException(branchId));
		if (!branch.isActive()) {
			throw new BusinessException("No se puede modificar la configuración de una sucursal desactivada");
		}
		BranchProduct branchProduct = branchProductRepository.findByBranchIdAndProductId(branchId, productId)
			.orElseThrow(() -> new BranchProductNotFoundException(branchId, productId));
		// priceOverride null limpia el override: el producto vuelve al precio base (RN US-14-02).
		branchProduct.setPriceOverride(priceOverride);
		if (available != null) {
			branchProduct.setAvailable(available);
		}
		branchProductRepository.save(branchProduct);
		return branchProduct.getProduct();
	}

	// US-15-07: actualización masiva de disponibilidad de productos para una sucursal.
	// El batch es atómico por la transacción propia de saveAll (criterio US-15-05).
	// A propósito SIN @Transactional: combinar @Transactional + @CacheEvict en el mismo
	// método deja el orden entre el advisor de transacción y el de cache sin garantía, y
	// la evicción podría ejecutarse ANTES del commit → una lectura concurrente repuebla el
	// cache con el dato viejo (bajo READ COMMITTED) y queda stale. Con @CacheEvict solo, la
	// evicción corre después de que saveAll commitea (mismo criterio que updateBranchConfig).
	// US-15-08: todos los productos de la sucursal con su disponibilidad (available true y
	// false), para el checklist de "Gestionar productos". Incluye sucursales inactivas a
	// propósito: la lectura NO aplica el guard de sucursal activa (solo la escritura lo hace).
	public List<BranchProduct> getBranchProducts(Integer branchId) {
		return branchProductRepository.findByBranchIdWithProductAndCategory(branchId);
	}

	@CacheEvict(value = "menu", key = "#branchId")
	public int updateBranchProductsAvailability(Integer branchId, List<Integer> productIds, boolean available) {
		// Mismo guard de escritura que updateBranchConfig (US-15-06): una sucursal
		// desactivada se rechaza (422) sin tocar ningún BranchProduct.
		Branch branch = branchRepository.findById(branchId)
			.orElseThrow(() -> new BranchNotFoundException(branchId));
		if (!branch.isActive()) {
			throw new BusinessException("No se puede modificar la configuración de una sucursal desactivada");
		}
		if (productIds == null || productIds.isEmpty()) {
			return 0;
		}
		// Los productId sin BranchProduct para esta sucursal no vienen en la query, así que
		// se ignoran sin romper el resto del batch.
		List<BranchProduct> toUpdate = branchProductRepository.findByBranchIdAndProductIdIn(branchId, productIds);
		toUpdate.forEach(bp -> bp.setAvailable(available));
		branchProductRepository.saveAll(toUpdate);
		return toUpdate.size();
	}

	// applyToAllBranches afecta potencialmente todas las sucursales (override limpiado) y,
	// aun en false, el nuevo precio base afecta a las sucursales sin override. Por eso se
	// evicta el menú completo en ambos casos.
	@CacheEvict(value = "menu", allEntries = true)
	public Product updatePrice(Integer productId, BigDecimal price, boolean applyToAllBranches) {
		Product product = findById(productId);
		product.setPrice(price);
		Product saved = repository.save(product);
		if (applyToAllBranches) {
			List<BranchProduct> branchProducts = branchProductRepository.findByProductId(productId);
			branchProducts.forEach(bp -> bp.setPriceOverride(null));
			branchProductRepository.saveAll(branchProducts);
		}
		return saved;
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
