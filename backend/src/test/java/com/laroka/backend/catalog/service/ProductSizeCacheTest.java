package com.laroka.backend.catalog.service;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.entity.ProductSizeName;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.ProductSizeRepository;

/**
 * US-SIZE-04: los tres caminos de escritura de tamaños evictan el cache "menu".
 *
 * Es crítico y no lo puede cubrir un test unitario: @CacheEvict lo aplica el proxy de
 * Spring, así que con mocks la anotación no se ejecuta y el test pasaría igual estando rota.
 * Acá se llama al service real y se cuenta cuántas veces se toca la DB — si la evicción no
 * ocurre, la segunda lectura del menú sale del cache y la cuenta queda en 1.
 *
 * El riesgo concreto que cubre: desde US-SIZE-F-02 los tamaños y sus precios viajan DENTRO
 * del valor cacheado del menú. Sin evict, el ADMIN cambia un precio y el cliente sigue
 * viendo el viejo hasta que expire el TTL (10 minutos).
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductSizeCacheTest {

	private static final int BRANCH_ID = 1;
	private static final int TENANT_ID = 1;
	private static final int PRODUCT_ID = 1;

	@MockitoSpyBean
	BranchProductRepository branchProductRepository;

	@Autowired ProductService productService;
	@Autowired ProductSizeService productSizeService;
	@Autowired ProductSizeRepository productSizeRepository;
	@Autowired CacheManager cacheManager;
	@Autowired JdbcTemplate jdbcTemplate;

	@BeforeEach
	void seed() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product_size, product_size, branch_product, product, category, "
				+ "category_type, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "test.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Test Branch", "Test Address", TENANT_ID);
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, allows_sizes, active) VALUES (?, ?, ?, ?)",
			"Pizza", true, true, true);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			"Muzzarella", new BigDecimal("15000.00"), 1, TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)",
			BRANCH_ID, PRODUCT_ID);

		cacheManager.getCache("menu").clear();
		clearInvocations(branchProductRepository);
	}

	private ProductSize seedChica() {
		return productSizeService.create(PRODUCT_ID, ProductSizeName.CHICA, new BigDecimal("9000.00"));
	}

	// Cuenta cuántas veces getMenuForBranch fue efectivamente a la DB.
	private void verifyMenuHitDatabase(int times) {
		verify(branchProductRepository, times(times)).findByBranchIdWithProductAndCategory(BRANCH_ID);
	}

	@Test
	void create_evictsMenuCache() {
		productService.getMenuForBranch(BRANCH_ID);
		seedChica();
		productService.getMenuForBranch(BRANCH_ID);

		// Sin evicción la segunda lectura saldría del cache y esto sería 1.
		verifyMenuHitDatabase(2);
	}

	@Test
	void update_evictsMenuCache() {
		ProductSize chica = seedChica();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(BRANCH_ID);
		productSizeService.update(PRODUCT_ID, chica.getId(), new BigDecimal("9900.00"), null);
		productService.getMenuForBranch(BRANCH_ID);

		verifyMenuHitDatabase(2);
	}

	@Test
	void deactivate_evictsMenuCache() {
		ProductSize chica = seedChica();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(BRANCH_ID);
		productSizeService.update(PRODUCT_ID, chica.getId(), null, false);
		productService.getMenuForBranch(BRANCH_ID);

		verifyMenuHitDatabase(2);
	}

	@Test
	void updateBranchOverride_evictsMenuCacheOfThatBranch() {
		ProductSize chica = seedChica();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(BRANCH_ID);
		productSizeService.updateBranchOverride(BRANCH_ID, PRODUCT_ID, chica.getId(), new BigDecimal("9900.00"));
		productService.getMenuForBranch(BRANCH_ID);

		verifyMenuHitDatabase(2);
	}

	@Test
	void clearingBranchOverride_alsoEvictsMenuCache() {
		ProductSize chica = seedChica();
		productSizeService.updateBranchOverride(BRANCH_ID, PRODUCT_ID, chica.getId(), new BigDecimal("9900.00"));
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(BRANCH_ID);
		productSizeService.updateBranchOverride(BRANCH_ID, PRODUCT_ID, chica.getId(), null);
		productService.getMenuForBranch(BRANCH_ID);

		verifyMenuHitDatabase(2);
	}

	@Test
	void menuReflectsTheNewPriceAfterAnOverride() {
		// El contrato que realmente importa: no sólo que se evicte, sino que la lectura
		// siguiente traiga el precio nuevo.
		ProductSize chica = seedChica();
		productService.getMenuForBranch(BRANCH_ID);
		productSizeService.updateBranchOverride(BRANCH_ID, PRODUCT_ID, chica.getId(), new BigDecimal("9900.00"));

		BranchMenu menu = productService.getMenuForBranch(BRANCH_ID);

		org.assertj.core.api.Assertions
			.assertThat(menu.sizesByProductId().get(PRODUCT_ID).get(0).price())
			.isEqualByComparingTo("9900.00");
	}
}
