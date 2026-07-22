package com.laroka.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.CategoryType;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.tenant.entity.Tenant;

@SpringBootTest
@ActiveProfiles("test")
class MenuCacheTest {

	@MockitoSpyBean
	BranchProductRepository branchProductRepository;

	@Autowired
	ProductService productService;

	@Autowired
	CategoryService categoryService;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void insertTestData() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product, product, category, category_type, staff_user, branch, tenant "
				+ "RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "test.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)", "Test Branch", "Test Address", 1);
		// category_type es tabla maestra sin seed en la migración (V31): lo carga el test.
		jdbcTemplate.update("INSERT INTO category_type (name, allows_half_and_half, allows_sizes) VALUES (?, ?, ?)",
			"Pizzas", true, true);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Test Category", 1, 1);
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			"Test Pizza", new BigDecimal("10.00"), 1, 1);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", 1, 1);
	}

	@BeforeEach
	void setUp() {
		cacheManager.getCache("menu").clear();
		clearInvocations(branchProductRepository);
	}

	@Test
	void getMenuForBranch_secondCallSameBranchId_hitsCache() {
		productService.getMenuForBranch(1);
		productService.getMenuForBranch(1);

		verify(branchProductRepository, times(1)).findByBranchIdWithProductAndCategory(1);
	}

	@Test
	void updateAvailability_evictsMenuCache() {
		List<BranchProduct> initial = branchProductRepository.findByBranchIdWithProductAndCategory(1);
		Assumptions.assumeTrue(!initial.isEmpty(), "Branch 1 must have at least one available product");
		Integer productId = initial.get(0).getProduct().getId();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(1);
		productService.updateAvailability(productId, false, 1);
		productService.getMenuForBranch(1);

		verify(branchProductRepository, times(2)).findByBranchIdWithProductAndCategory(1);

		productService.updateAvailability(productId, true, 1);
	}

	@Test
	void update_evictsMenuCache() {
		List<BranchProduct> initial = branchProductRepository.findByBranchIdWithProductAndCategory(1);
		Assumptions.assumeTrue(!initial.isEmpty(), "Branch 1 must have at least one product");
		com.laroka.backend.catalog.entity.Product existing = initial.get(0).getProduct();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(1);

		// Un update general del producto (aquí, cambio de imageUrl) debe invalidar el menú
		// completo (allEntries=true), no una sola sucursal.
		com.laroka.backend.catalog.entity.Product updates = com.laroka.backend.catalog.entity.Product.builder()
			.name(existing.getName())
			.description(existing.getDescription())
			.price(existing.getPrice())
			.imageUrl("https://cdn.r2.dev/1/products/updated.png")
			.category(com.laroka.backend.catalog.entity.Category.builder().id(1).build())
			.tenant(com.laroka.backend.tenant.entity.Tenant.builder().id(1).build())
			.build();
		productService.update(existing.getId(), updates);

		productService.getMenuForBranch(1);

		verify(branchProductRepository, times(2)).findByBranchIdWithProductAndCategory(1);
	}

	// Los tres tests que siguen cubren los métodos que no pueden usar @CacheEvict (ver
	// MenuCacheEvictionListener) y evictan vía MenuCacheEvictionEvent en AFTER_COMMIT. Corren
	// sin @Transactional a propósito: así la transacción del service commitea de verdad y el
	// listener llega a dispararse.
	@Test
	void delete_evictsMenuCacheAfterCommit() {
		List<BranchProduct> initial = branchProductRepository.findByBranchIdWithProductAndCategory(1);
		Assumptions.assumeTrue(!initial.isEmpty(), "Branch 1 must have at least one product");
		Integer productId = initial.get(0).getProduct().getId();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(1);
		productService.delete(productId);

		BranchMenu afterDelete = productService.getMenuForBranch(1);

		// Si la evicción no hubiera corrido, esta segunda lectura vendría del cache y el
		// repositorio se habría consultado una sola vez.
		verify(branchProductRepository, times(2)).findByBranchIdWithProductAndCategory(1);
		assertThat(afterDelete.branchProducts())
			.noneMatch(bp -> bp.getProduct().getId().equals(productId));
	}

	@Test
	void updatePrice_evictsMenuCacheAfterCommit() {
		List<BranchProduct> initial = branchProductRepository.findByBranchIdWithProductAndCategory(1);
		Assumptions.assumeTrue(!initial.isEmpty(), "Branch 1 must have at least one product");
		Integer productId = initial.get(0).getProduct().getId();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(1);
		productService.updatePrice(productId, new BigDecimal("12345.00"), true);

		BranchMenu afterUpdate = productService.getMenuForBranch(1);

		verify(branchProductRepository, times(2)).findByBranchIdWithProductAndCategory(1);
		assertThat(afterUpdate.branchProducts())
			.filteredOn(bp -> bp.getProduct().getId().equals(productId))
			.allSatisfy(bp -> {
				assertThat(bp.getProduct().getPrice()).isEqualByComparingTo("12345.00");
				// applyToAllBranches=true limpia los overrides en la misma transacción.
				assertThat(bp.getPriceOverride()).isNull();
			});
	}

	@Test
	void categoryDelete_evictsMenuCacheAfterCommit() {
		Assumptions.assumeTrue(
			!branchProductRepository.findByBranchIdWithProductAndCategory(1).isEmpty(),
			"Branch 1 must have at least one product");
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(1);
		// Borra la categoría 1 y en cascada su único producto con sus branch_product.
		categoryService.delete(1);

		BranchMenu afterDelete = productService.getMenuForBranch(1);

		verify(branchProductRepository, times(2)).findByBranchIdWithProductAndCategory(1);
		assertThat(afterDelete.branchProducts()).isEmpty();
	}

	// categoryService.update() sí usa @CacheEvict directo: hace una sola escritura, no lleva
	// @Transactional y por lo tanto no hay orden indeterminado entre advisors que resolver.
	@Test
	void categoryUpdate_evictsMenuCache() {
		clearInvocations(branchProductRepository);
		productService.getMenuForBranch(1);

		Category updates = Category.builder()
			.name("Categoría Renombrada")
			.tenant(Tenant.builder().id(1).build())
			.categoryType(CategoryType.builder().id(1).build())
			.build();
		categoryService.update(1, updates);

		BranchMenu afterUpdate = productService.getMenuForBranch(1);

		verify(branchProductRepository, times(2)).findByBranchIdWithProductAndCategory(1);
		assertThat(afterUpdate.branchProducts())
			.isNotEmpty()
			.allSatisfy(bp -> assertThat(bp.getProduct().getCategory().getName())
				.isEqualTo("Categoría Renombrada"));
	}
}
