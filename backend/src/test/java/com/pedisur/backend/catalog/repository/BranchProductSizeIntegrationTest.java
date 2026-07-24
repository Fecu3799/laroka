package com.pedisur.backend.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.branch.repository.BranchRepository;
import com.pedisur.backend.catalog.entity.BranchProductSize;
import com.pedisur.backend.catalog.entity.ProductSize;
import com.pedisur.backend.catalog.entity.ProductSizeName;
import com.pedisur.backend.catalog.service.ProductSizeService;

/**
 * US-SIZE-02: verifica el mapeo de BranchProductSize contra el esquema real (V36) y la
 * resolución del precio efectivo por sucursal contra DB, no contra mocks.
 *
 * El interés puntual es la clave compuesta: findByBranchIdAndProductSizeId navega
 * branch.id y productSize.id sobre un @IdClass de dos @ManyToOne.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BranchProductSizeIntegrationTest {

	private static final int TENANT_ID = 1;
	private static final int BRANCH_A = 1;
	private static final int BRANCH_B = 2;

	@Autowired BranchProductSizeRepository branchProductSizeRepository;
	@Autowired ProductSizeRepository productSizeRepository;
	@Autowired BranchRepository branchRepository;
	@Autowired ProductSizeService productSizeService;
	@Autowired JdbcTemplate jdbcTemplate;

	private ProductSize grande;
	private ProductSize chica;

	@BeforeAll
	void seed() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product_size, product_size, branch_product, product, category, "
				+ "category_type, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Centro", "Av. Siempreviva 1", TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Norte", "Av. Siempreviva 2", TENANT_ID);
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, allows_sizes, active) VALUES (?, ?, ?, ?)",
			"Pizza", true, true, true);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			"Muzzarella", new BigDecimal("10000.00"), 1, TENANT_ID);
		jdbcTemplate.update(
			"INSERT INTO product_size (product_id, size, price, active) VALUES (?, ?, ?, ?)",
			1, "GRANDE", new BigDecimal("15000.00"), true);
		jdbcTemplate.update(
			"INSERT INTO product_size (product_id, size, price, active) VALUES (?, ?, ?, ?)",
			1, "CHICA", new BigDecimal("9000.00"), true);

		grande = productSizeRepository.findByProductIdAndSize(1, ProductSizeName.GRANDE).orElseThrow();
		chica = productSizeRepository.findByProductIdAndSize(1, ProductSizeName.CHICA).orElseThrow();
	}

	@Test
	void resolvesBasePriceWhenBranchHasNoOverrideRow() {
		// chica no tiene fila en branch_product_size para ninguna sucursal.
		assertThat(productSizeService.resolveEffectivePrice(BRANCH_A, chica)).isEqualByComparingTo("9000.00");
	}

	@Test
	void overrideIsScopedToItsOwnBranch() {
		branchProductSizeRepository.save(BranchProductSize.builder()
			.branch(branch(BRANCH_A))
			.productSize(grande)
			.priceOverride(new BigDecimal("18000.00"))
			.build());

		// Sucursal A toma el override; B, que no tiene fila, sigue en el precio base.
		assertThat(productSizeService.resolveEffectivePrice(BRANCH_A, grande)).isEqualByComparingTo("18000.00");
		assertThat(productSizeService.resolveEffectivePrice(BRANCH_B, grande)).isEqualByComparingTo("15000.00");
	}

	@Test
	void samePairIsUniqueButDifferentBranchesCoexist() {
		branchProductSizeRepository.save(BranchProductSize.builder()
			.branch(branch(BRANCH_B))
			.productSize(chica)
			.priceOverride(new BigDecimal("9500.00"))
			.build());
		// Segundo save sobre la misma PK (branch B + chica): es un merge, no una fila nueva.
		branchProductSizeRepository.save(BranchProductSize.builder()
			.branch(branch(BRANCH_B))
			.productSize(chica)
			.priceOverride(new BigDecimal("9900.00"))
			.build());

		Integer rows = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM branch_product_size WHERE branch_id = ? AND product_size_id = ?",
			Integer.class, BRANCH_B, chica.getId());

		assertThat(rows).isEqualTo(1);
		assertThat(productSizeService.resolveEffectivePrice(BRANCH_B, chica)).isEqualByComparingTo("9900.00");
	}

	private Branch branch(Integer branchId) {
		return branchRepository.findById(branchId).orElseThrow();
	}
}
