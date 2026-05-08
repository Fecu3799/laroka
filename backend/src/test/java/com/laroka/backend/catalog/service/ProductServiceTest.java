package com.laroka.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
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

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock private ProductRepository productRepository;
	@Mock private CategoryRepository categoryRepository;
	@Mock private BranchRepository branchRepository;
	@Mock private BranchProductRepository branchProductRepository;
	@Mock private TenantRepository tenantRepository;

	@InjectMocks
	private ProductService service;

	private Tenant tenant() {
		return Tenant.builder().id(1).name("LaRoka").build();
	}

	private Branch branch(Tenant tenant) {
		return Branch.builder().id(1).name("Playa Unión").address("Av. Principal 123").tenant(tenant).build();
	}

	private Category category(Tenant tenant) {
		return Category.builder().id(1).name("Pizzas").tenant(tenant).build();
	}

	private Product product(Category category, Tenant tenant) {
		return Product.builder()
			.id(1)
			.name("Muzzarella")
			.description("Clásica")
			.price(new BigDecimal("2800.00"))
			.category(category)
			.tenant(tenant)
			.build();
	}

	private BranchProduct branchProduct(Branch branch, Product product, BigDecimal priceOverride) {
		return BranchProduct.builder()
			.branch(branch)
			.product(product)
			.available(true)
			.priceOverride(priceOverride)
			.build();
	}

	// --- findById ---

	@Test
	void findById_returnsExistingProduct() {
		Tenant p = tenant();
		Product product = product(category(p), p);
		when(productRepository.findById(1)).thenReturn(Optional.of(product));

		Product result = service.findById(1);

		assertThat(result.getId()).isEqualTo(1);
		assertThat(result.getName()).isEqualTo("Muzzarella");
	}

	@Test
	void findById_notFound_throwsProductNotFoundException() {
		when(productRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(99))
			.isInstanceOf(ProductNotFoundException.class);
	}

	// --- getMenuForBranch ---

	@Test
	void getMenuForBranch_invalidBranch_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getMenuForBranch(99))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void getMenuForBranch_returnsOnlyAvailableProducts() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, null);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdAndAvailableTrue(1)).thenReturn(List.of(bp));

		List<BranchProduct> result = service.getMenuForBranch(1);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getAvailable()).isTrue();
	}

	@Test
	void getMenuForBranch_withoutPriceOverride_branchProductHasNullOverride() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, null);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdAndAvailableTrue(1)).thenReturn(List.of(bp));

		List<BranchProduct> result = service.getMenuForBranch(1);

		assertThat(result.get(0).getPriceOverride()).isNull();
		assertThat(result.get(0).getProduct().getPrice()).isEqualByComparingTo("2800.00");
	}

	@Test
	void getMenuForBranch_withPriceOverride_branchProductHasOverride() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, new BigDecimal("3100.00"));
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdAndAvailableTrue(1)).thenReturn(List.of(bp));

		List<BranchProduct> result = service.getMenuForBranch(1);

		assertThat(result.get(0).getPriceOverride()).isEqualByComparingTo("3100.00");
	}

	// --- findByCategory ---

	@Test
	void findByCategory_invalidCategory_throwsCategoryNotFoundException() {
		when(categoryRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByCategory(99))
			.isInstanceOf(CategoryNotFoundException.class);
	}

	// --- findByTenant ---

	@Test
	void findByTenant_invalidTenant_throwsTenantNotFoundException() {
		when(tenantRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByTenant(99))
			.isInstanceOf(TenantNotFoundException.class);
	}

	// --- create ---

	@Test
	void create_validProduct_savesAndReturns() {
		Tenant p = tenant();
		Category c = category(p);
		Product product = product(c, p);
		when(categoryRepository.findById(1)).thenReturn(Optional.of(c));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(productRepository.save(any(Product.class))).thenReturn(product);

		Product result = service.create(product);

		assertThat(result.getName()).isEqualTo("Muzzarella");
		verify(productRepository).save(product);
	}

	@Test
	void create_invalidCategory_throwsCategoryNotFoundException() {
		Tenant p = tenant();
		Product product = product(category(p), p);
		when(categoryRepository.findById(1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(product))
			.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void create_invalidTenant_throwsTenantNotFoundException() {
		Tenant p = tenant();
		Product product = product(category(p), p);
		when(categoryRepository.findById(1)).thenReturn(Optional.of(category(p)));
		when(tenantRepository.findById(1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(product))
			.isInstanceOf(TenantNotFoundException.class);
	}

	// --- update ---

	@Test
	void update_existingProduct_updatesAndReturns() {
		Tenant p = tenant();
		Category c = category(p);
		Product existing = product(c, p);
		Product updates = Product.builder()
			.name("Fugazzeta")
			.description("Con cebolla")
			.price(new BigDecimal("3400.00"))
			.category(c)
			.tenant(p)
			.build();
		when(productRepository.findById(1)).thenReturn(Optional.of(existing));
		when(categoryRepository.findById(1)).thenReturn(Optional.of(c));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(productRepository.save(any(Product.class))).thenReturn(existing);

		Product result = service.update(1, updates);

		assertThat(result.getName()).isEqualTo("Fugazzeta");
	}

	@Test
	void update_notFound_throwsProductNotFoundException() {
		when(productRepository.findById(99)).thenReturn(Optional.empty());
		Tenant p = tenant();

		assertThatThrownBy(() -> service.update(99, product(category(p), p)))
			.isInstanceOf(ProductNotFoundException.class);
	}

	// --- delete ---

	@Test
	void delete_existingProduct_deletes() {
		Tenant p = tenant();
		Product product = product(category(p), p);
		when(productRepository.findById(1)).thenReturn(Optional.of(product));

		service.delete(1);

		verify(productRepository).delete(product);
	}

	@Test
	void delete_notFound_throwsProductNotFoundException() {
		when(productRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99))
			.isInstanceOf(ProductNotFoundException.class);
	}

	// --- updateAvailability ---

	@Test
	void updateAvailability_validBranchProduct_updatesAvailability() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, null);
		when(branchProductRepository.findByBranchIdAndProductId(1, 1)).thenReturn(Optional.of(bp));
		when(branchProductRepository.save(any(BranchProduct.class))).thenReturn(bp);

		Product result = service.updateAvailability(1, false, 1);

		assertThat(bp.getAvailable()).isFalse();
		assertThat(result.getId()).isEqualTo(1);
		verify(branchProductRepository).save(bp);
	}

	@Test
	void updateAvailability_productNotInBranch_throwsBranchProductNotFoundException() {
		when(branchProductRepository.findByBranchIdAndProductId(1, 99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateAvailability(99, false, 1))
			.isInstanceOf(BranchProductNotFoundException.class);
	}

	@Test
	void updateAvailability_nullBranchId_throwsBusinessException() {
		assertThatThrownBy(() -> service.updateAvailability(1, false, null))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("Branch ID");
	}
}
