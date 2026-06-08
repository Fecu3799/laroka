package com.laroka.backend.catalog.service;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
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
