package com.laroka.backend.catalog.service;

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
import com.laroka.backend.catalog.repository.BranchProductRepository;

@SpringBootTest
@ActiveProfiles("test")
class MenuCacheTest {

	@MockitoSpyBean
	BranchProductRepository branchProductRepository;

	@Autowired
	ProductService productService;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void insertTestData() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "test.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)", "Test Branch", "Test Address", 1);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Test Category", 1);
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

		verify(branchProductRepository, times(1)).findByBranchIdAndAvailableTrue(1);
	}

	@Test
	void updateAvailability_evictsMenuCache() {
		List<BranchProduct> initial = branchProductRepository.findByBranchIdAndAvailableTrue(1);
		Assumptions.assumeTrue(!initial.isEmpty(), "Branch 1 must have at least one available product");
		Integer productId = initial.get(0).getProduct().getId();
		clearInvocations(branchProductRepository);

		productService.getMenuForBranch(1);
		productService.updateAvailability(productId, false, 1);
		productService.getMenuForBranch(1);

		verify(branchProductRepository, times(2)).findByBranchIdAndAvailableTrue(1);

		productService.updateAvailability(productId, true, 1);
	}
}
