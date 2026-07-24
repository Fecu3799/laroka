package com.pedisur.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.branch.exception.BranchNotFoundException;
import com.pedisur.backend.branch.repository.BranchRepository;
import com.pedisur.backend.catalog.entity.BranchProduct;
import com.pedisur.backend.catalog.entity.Category;
import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.catalog.exception.BranchProductNotFoundException;
import com.pedisur.backend.catalog.exception.CategoryNotFoundException;
import com.pedisur.backend.catalog.exception.ProductNotFoundException;
import com.pedisur.backend.catalog.repository.BranchProductRepository;
import com.pedisur.backend.catalog.repository.CategoryRepository;
import com.pedisur.backend.catalog.repository.ProductRepository;
import com.pedisur.backend.tenant.entity.Tenant;
import com.pedisur.backend.tenant.exception.TenantNotFoundException;
import com.pedisur.backend.tenant.repository.TenantRepository;
import com.pedisur.backend.shared.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock private ProductRepository productRepository;
	@Mock private CategoryRepository categoryRepository;
	@Mock private BranchRepository branchRepository;
	@Mock private BranchProductRepository branchProductRepository;
	@Mock private com.pedisur.backend.catalog.service.ProductSizeService productSizeService;
	@Mock private TenantRepository tenantRepository;
	@Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

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
	void getMenuForBranch_returnsAllProductsIncludingUnavailable() {
		// US-15-11: el menú ya no filtra por available=true — retorna todos los
		// productos de la sucursal, disponibles y no disponibles.
		Tenant p = tenant();
		Branch b = branch(p);
		Product available = product(category(p), p);
		Product unavailable = product(category(p), p);
		BranchProduct availableBp = branchProduct(b, available, null);
		BranchProduct unavailableBp = branchProduct(b, unavailable, null);
		unavailableBp.setAvailable(false);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdWithProductAndCategory(1))
			.thenReturn(List.of(availableBp, unavailableBp));

		List<BranchProduct> result = service.getMenuForBranch(1).branchProducts();

		assertThat(result).hasSize(2);
		assertThat(result).extracting(BranchProduct::getAvailable)
			.containsExactlyInAnyOrder(true, false);
	}

	@Test
	void getMenuForBranch_withoutPriceOverride_branchProductHasNullOverride() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, null);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdWithProductAndCategory(1)).thenReturn(List.of(bp));

		List<BranchProduct> result = service.getMenuForBranch(1).branchProducts();

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
		when(branchProductRepository.findByBranchIdWithProductAndCategory(1)).thenReturn(List.of(bp));

		List<BranchProduct> result = service.getMenuForBranch(1).branchProducts();

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
	void create_generatesBranchProductForEachTenantBranch() {
		Tenant p = tenant();
		Category c = category(p);
		Product product = product(c, p);
		Branch b1 = Branch.builder().id(1).name("Playa Unión").tenant(p).build();
		Branch b2 = Branch.builder().id(2).name("Puerto Madryn").tenant(p).build();
		when(categoryRepository.findById(1)).thenReturn(Optional.of(c));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(productRepository.save(any(Product.class))).thenReturn(product);
		when(branchRepository.findByTenantId(1)).thenReturn(List.of(b1, b2));
		when(branchProductRepository.existsByBranchIdAndProductId(anyInt(), anyInt())).thenReturn(false);

		service.create(product);

		ArgumentCaptor<BranchProduct> captor = ArgumentCaptor.forClass(BranchProduct.class);
		verify(branchProductRepository, times(2)).save(captor.capture());
		assertThat(captor.getAllValues())
			.extracting(bp -> bp.getBranch().getId(), BranchProduct::getAvailable, BranchProduct::getPriceOverride)
			.containsExactlyInAnyOrder(
				tuple(1, true, null),
				tuple(2, true, null));
	}

	@Test
	void create_existingBranchProduct_notDuplicated() {
		Tenant p = tenant();
		Category c = category(p);
		Product product = product(c, p);
		Branch b1 = Branch.builder().id(1).name("Playa Unión").tenant(p).build();
		Branch b2 = Branch.builder().id(2).name("Puerto Madryn").tenant(p).build();
		when(categoryRepository.findById(1)).thenReturn(Optional.of(c));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(productRepository.save(any(Product.class))).thenReturn(product);
		when(branchRepository.findByTenantId(1)).thenReturn(List.of(b1, b2));
		when(branchProductRepository.existsByBranchIdAndProductId(1, 1)).thenReturn(true);
		when(branchProductRepository.existsByBranchIdAndProductId(2, 1)).thenReturn(false);

		service.create(product);

		ArgumentCaptor<BranchProduct> captor = ArgumentCaptor.forClass(BranchProduct.class);
		verify(branchProductRepository, times(1)).save(captor.capture());
		assertThat(captor.getValue().getBranch().getId()).isEqualTo(2);
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
		// La evicción del menú viaja por el evento, no por @CacheEvict: MenuCacheEvictionListener
		// lo consume en AFTER_COMMIT para no evictar antes de que el commit esté confirmado.
		verify(eventPublisher).publishEvent(
			com.pedisur.backend.catalog.event.MenuCacheEvictionEvent.productDeleted(1));
	}

	@Test
	void delete_notFound_throwsProductNotFoundException() {
		when(productRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99))
			.isInstanceOf(ProductNotFoundException.class);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
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

	// --- getBranchProductConfig ---

	@Test
	void getBranchProductConfig_existingProduct_returnsBranchProducts() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, new BigDecimal("3100.00"));
		when(productRepository.findById(1)).thenReturn(Optional.of(product));
		when(branchProductRepository.findConfigByProductId(1)).thenReturn(List.of(bp));

		List<BranchProduct> result = service.getBranchProductConfig(1).branchProducts();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getPriceOverride()).isEqualByComparingTo("3100.00");
		verify(branchProductRepository).findConfigByProductId(1);
	}

	@Test
	void getBranchProductConfig_productNotFound_throwsProductNotFoundException() {
		when(productRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getBranchProductConfig(99))
			.isInstanceOf(ProductNotFoundException.class);
	}

	// US-15-06: la config por sucursal excluye sucursales inactivas sin tocar el BranchProduct.

	@Test
	void getBranchProductConfig_excludesInactiveBranches() {
		Tenant p = tenant();
		Product product = product(category(p), p);
		Branch active = Branch.builder().id(1).name("Playa Unión").tenant(p).active(true).build();
		Branch inactive = Branch.builder().id(2).name("Trelew").tenant(p).active(false).build();
		BranchProduct bpActive = branchProduct(active, product, new BigDecimal("3100.00"));
		BranchProduct bpInactive = branchProduct(inactive, product, new BigDecimal("9999.00"));
		when(productRepository.findById(1)).thenReturn(Optional.of(product));
		when(branchProductRepository.findConfigByProductId(1)).thenReturn(List.of(bpActive, bpInactive));

		List<BranchProduct> result = service.getBranchProductConfig(1).branchProducts();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getBranch().getId()).isEqualTo(1);
	}

	@Test
	void getBranchProductConfig_reactivatedBranch_reappearsWithPreviousValues() {
		Tenant p = tenant();
		Product product = product(category(p), p);
		Branch branch = Branch.builder().id(2).name("Trelew").tenant(p).active(false).build();
		// BranchProduct con valores propios; nunca se modifica al filtrar.
		BranchProduct bp = branchProduct(branch, product, new BigDecimal("4200.00"));
		bp.setAvailable(false);
		when(productRepository.findById(1)).thenReturn(Optional.of(product));
		when(branchProductRepository.findConfigByProductId(1)).thenReturn(List.of(bp));

		// Sucursal inactiva → no aparece.
		assertThat(service.getBranchProductConfig(1).branchProducts()).isEmpty();

		// Se reactiva la sucursal (el mismo BranchProduct, sin resetear valores).
		branch.setActive(true);

		List<BranchProduct> result = service.getBranchProductConfig(1).branchProducts();
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getPriceOverride()).isEqualByComparingTo("4200.00");
		assertThat(result.get(0).getAvailable()).isFalse();
	}

	// --- updateBranchConfig ---

	@Test
	void updateBranchConfig_nullPriceOverride_clearsOverride() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, new BigDecimal("3100.00"));
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdAndProductId(1, 1)).thenReturn(Optional.of(bp));
		when(branchProductRepository.save(any(BranchProduct.class))).thenReturn(bp);

		service.updateBranchConfig(1, 1, null, null);

		assertThat(bp.getPriceOverride()).isNull();
		verify(branchProductRepository).save(bp);
	}

	@Test
	void updateBranchConfig_setsOverrideAndAvailability() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp = branchProduct(b, product, null);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdAndProductId(1, 1)).thenReturn(Optional.of(bp));
		when(branchProductRepository.save(any(BranchProduct.class))).thenReturn(bp);

		service.updateBranchConfig(1, 1, new BigDecimal("3300.00"), false);

		assertThat(bp.getPriceOverride()).isEqualByComparingTo("3300.00");
		assertThat(bp.getAvailable()).isFalse();
	}

	@Test
	void updateBranchConfig_productNotInBranch_throwsBranchProductNotFoundException() {
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch(tenant())));
		when(branchProductRepository.findByBranchIdAndProductId(1, 99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateBranchConfig(99, 1, null, null))
			.isInstanceOf(BranchProductNotFoundException.class);
	}

	@Test
	void updateBranchConfig_inactiveBranch_rejectsUpdate() {
		// US-15-06: guard de escritura — una sucursal desactivada no puede modificarse
		// vía API directa aunque el frontend la oculte.
		Branch inactive = Branch.builder().id(1).name("Trelew").tenant(tenant()).active(false).build();
		when(branchRepository.findById(1)).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.updateBranchConfig(1, 1, new BigDecimal("3300.00"), true))
			.isInstanceOf(BusinessException.class);
		verify(branchProductRepository, never()).save(any());
	}

	// --- US-15-08: getBranchProducts (lista con disponibilidad, incluye inactivas) ---

	@Test
	void getBranchProducts_returnsProductsWithTheirAvailability() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product prodA = product(category(p), p);
		Product prodB = product(category(p), p);
		BranchProduct available = branchProduct(b, prodA, null);
		BranchProduct hidden = branchProduct(b, prodB, null);
		hidden.setAvailable(false);
		when(branchProductRepository.findByBranchIdWithProductAndCategory(1))
			.thenReturn(List.of(available, hidden));

		List<BranchProduct> result = service.getBranchProducts(1);

		assertThat(result).hasSize(2);
		assertThat(result).extracting(BranchProduct::getAvailable).containsExactly(true, false);
	}

	@Test
	void getBranchProducts_noActiveGuard_worksForInactiveBranch() {
		// La lectura NO consulta branchRepository (no aplica guard de sucursal activa):
		// una sucursal inactiva devuelve sus productos igual.
		when(branchProductRepository.findByBranchIdWithProductAndCategory(2)).thenReturn(List.of());

		List<BranchProduct> result = service.getBranchProducts(2);

		assertThat(result).isEmpty();
		verify(branchRepository, never()).findById(any());
	}

	// --- US-15-07: updateBranchProductsAvailability (bulk) ---

	@Test
	void updateBranchProductsAvailability_updatesMatchingBranchProducts() {
		Tenant p = tenant();
		Branch b = branch(p); // activa
		BranchProduct bp1 = branchProduct(b, Product.builder().id(10).build(), null);
		BranchProduct bp2 = branchProduct(b, Product.builder().id(11).build(), null);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(branchProductRepository.findByBranchIdAndProductIdIn(1, List.of(10, 11)))
			.thenReturn(List.of(bp1, bp2));

		int updated = service.updateBranchProductsAvailability(1, List.of(10, 11), false);

		assertThat(updated).isEqualTo(2);
		assertThat(bp1.getAvailable()).isFalse();
		assertThat(bp2.getAvailable()).isFalse();
		verify(branchProductRepository).saveAll(any());
		verify(branchProductRepository, never()).save(any());
	}

	@Test
	void updateBranchProductsAvailability_emptyList_returnsZeroWithoutQuery() {
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch(tenant())));

		int updated = service.updateBranchProductsAvailability(1, List.of(), true);

		assertThat(updated).isZero();
		verify(branchProductRepository, never()).findByBranchIdAndProductIdIn(any(), any());
		verify(branchProductRepository, never()).saveAll(any());
	}

	@Test
	void updateBranchProductsAvailability_ignoresProductIdsWithoutBranchProduct() {
		Tenant p = tenant();
		Branch b = branch(p);
		BranchProduct bp = branchProduct(b, Product.builder().id(10).build(), null);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		// productId 999 no tiene BranchProduct para esta sucursal → la query solo trae el de 10.
		when(branchProductRepository.findByBranchIdAndProductIdIn(1, List.of(10, 999)))
			.thenReturn(List.of(bp));

		int updated = service.updateBranchProductsAvailability(1, List.of(10, 999), true);

		assertThat(updated).isEqualTo(1);
		assertThat(bp.getAvailable()).isTrue();
		verify(branchProductRepository).saveAll(any());
	}

	@Test
	void updateBranchProductsAvailability_inactiveBranch_rejectsWithoutTouchingData() {
		Branch inactive = Branch.builder().id(1).name("Trelew").tenant(tenant()).active(false).build();
		when(branchRepository.findById(1)).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.updateBranchProductsAvailability(1, List.of(10), true))
			.isInstanceOf(BusinessException.class);
		verify(branchProductRepository, never()).findByBranchIdAndProductIdIn(any(), any());
		verify(branchProductRepository, never()).saveAll(any());
	}

	// --- updatePrice ---

	@Test
	void updatePrice_applyToAllBranchesTrue_clearsAllOverrides() {
		Tenant p = tenant();
		Branch b = branch(p);
		Product product = product(category(p), p);
		BranchProduct bp1 = branchProduct(b, product, new BigDecimal("3100.00"));
		BranchProduct bp2 = branchProduct(b, product, new BigDecimal("2950.00"));
		when(productRepository.findById(1)).thenReturn(Optional.of(product));
		when(productRepository.save(any(Product.class))).thenReturn(product);
		when(branchProductRepository.findByProductId(1)).thenReturn(List.of(bp1, bp2));

		Product result = service.updatePrice(1, new BigDecimal("3500.00"), true);

		assertThat(result.getPrice()).isEqualByComparingTo("3500.00");
		assertThat(bp1.getPriceOverride()).isNull();
		assertThat(bp2.getPriceOverride()).isNull();
		verify(branchProductRepository).saveAll(List.of(bp1, bp2));
		verify(eventPublisher).publishEvent(
			com.pedisur.backend.catalog.event.MenuCacheEvictionEvent.productPriceUpdated(1));
	}

	@Test
	void updatePrice_applyToAllBranchesFalse_doesNotTouchOverrides() {
		Tenant p = tenant();
		Product product = product(category(p), p);
		when(productRepository.findById(1)).thenReturn(Optional.of(product));
		when(productRepository.save(any(Product.class))).thenReturn(product);

		Product result = service.updatePrice(1, new BigDecimal("3500.00"), false);

		assertThat(result.getPrice()).isEqualByComparingTo("3500.00");
		verify(branchProductRepository, never()).findByProductId(any());
		verify(branchProductRepository, never()).saveAll(any());
		// El precio base afecta a las sucursales sin override, así que el menú se evicta
		// también con applyToAllBranches=false.
		verify(eventPublisher).publishEvent(
			com.pedisur.backend.catalog.event.MenuCacheEvictionEvent.productPriceUpdated(1));
	}
}
