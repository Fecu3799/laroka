package com.laroka.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.BranchProductSize;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.CategoryType;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.entity.ProductSizeName;
import com.laroka.backend.catalog.exception.ProductNotFoundException;
import com.laroka.backend.catalog.exception.ProductSizeNotFoundException;
import com.laroka.backend.catalog.exception.UnsupportedProductSizeException;
import com.laroka.backend.catalog.repository.BranchProductSizeRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.catalog.repository.ProductSizeRepository;
import com.laroka.backend.shared.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class ProductSizeServiceTest {

	private static final int BRANCH_ID = 1;
	private static final int SIZE_ID = 10;

	private static final int PRODUCT_ID = 7;

	@Mock
	private BranchProductSizeRepository branchProductSizeRepository;
	@Mock
	private ProductSizeRepository productSizeRepository;
	@Mock
	private ProductRepository productRepository;
	@Mock
	private BranchRepository branchRepository;

	@InjectMocks
	private ProductSizeService service;

	private final ProductSize grande = ProductSize.builder()
		.id(SIZE_ID)
		.size(ProductSizeName.GRANDE)
		.price(new BigDecimal("15000.00"))
		.active(true)
		.build();

	// ── Fixtures de escritura (US-SIZE-04) ──────────────────────────────────────

	private Product productWithSizesAllowed(boolean allowsSizes) {
		CategoryType type = CategoryType.builder()
			.id(1).name("Pizza").allowsSizes(allowsSizes).active(true).build();
		return Product.builder()
			.id(PRODUCT_ID).name("Muzzarella").price(new BigDecimal("15000.00"))
			.category(Category.builder().id(1).name("Pizzas").categoryType(type).build())
			.build();
	}

	private ProductSize chicaOf(Product product) {
		return ProductSize.builder()
			.id(SIZE_ID).product(product).size(ProductSizeName.CHICA)
			.price(new BigDecimal("9000.00")).active(true)
			.build();
	}

	private Branch activeBranch() {
		return Branch.builder().id(BRANCH_ID).name("Centro").active(true).build();
	}

	@Test
	void usesOverrideWhenBranchHasOne() {
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.of(BranchProductSize.builder()
				.priceOverride(new BigDecimal("17500.00"))
				.build()));

		assertThat(service.resolveEffectivePrice(BRANCH_ID, grande)).isEqualByComparingTo("17500.00");
	}

	@Test
	void fallsBackToBasePriceWhenNoRowForBranch() {
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.empty());

		assertThat(service.resolveEffectivePrice(BRANCH_ID, grande)).isEqualByComparingTo("15000.00");
	}

	@Test
	void fallsBackToBasePriceWhenRowExistsWithNullOverride() {
		// Fila presente pero sin override: equivalente a no tener fila. Este es el caso que
		// deja el backoffice al limpiar un override ya cargado (mismo criterio que
		// BranchProduct.priceOverride = null en US-14-02).
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.of(BranchProductSize.builder()
				.priceOverride(null)
				.build()));

		assertThat(service.resolveEffectivePrice(BRANCH_ID, grande)).isEqualByComparingTo("15000.00");
	}

	// ── create (US-SIZE-04) ─────────────────────────────────────────────────────

	@Test
	void create_chica_persistsActiveSizeWithItsPrice() {
		Product product = productWithSizesAllowed(true);
		when(productRepository.findByIdWithCategoryType(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(productSizeRepository.findByProductIdAndSize(PRODUCT_ID, ProductSizeName.CHICA))
			.thenReturn(Optional.empty());
		when(productSizeRepository.save(any(ProductSize.class))).thenAnswer(inv -> inv.getArgument(0));

		ProductSize created = service.create(PRODUCT_ID, ProductSizeName.CHICA, new BigDecimal("9000.00"));

		assertThat(created.getSize()).isEqualTo(ProductSizeName.CHICA);
		assertThat(created.getPrice()).isEqualByComparingTo("9000.00");
		assertThat(created.getActive()).isTrue();
		assertThat(created.getProduct().getId()).isEqualTo(PRODUCT_ID);
	}

	@Test
	void create_grande_isRejected() {
		// GRANDE es implícito: su precio es siempre product.price. Una fila GRANDE crearía una
		// segunda fuente de verdad. La validación vive acá, no sólo en la UI, porque el enum
		// sigue teniendo ambos valores y un POST directo podría colarla.
		when(productRepository.findByIdWithCategoryType(PRODUCT_ID))
			.thenReturn(Optional.of(productWithSizesAllowed(true)));

		assertThatThrownBy(() ->
			service.create(PRODUCT_ID, ProductSizeName.GRANDE, new BigDecimal("15000.00")))
			.isInstanceOf(UnsupportedProductSizeException.class);
		verify(productSizeRepository, never()).save(any());
	}

	@Test
	void create_categoryDoesNotAllowSizes_isRejected() {
		// Cierra DT-04: la regla no es expresable como constraint declarativo.
		when(productRepository.findByIdWithCategoryType(PRODUCT_ID))
			.thenReturn(Optional.of(productWithSizesAllowed(false)));

		assertThatThrownBy(() ->
			service.create(PRODUCT_ID, ProductSizeName.CHICA, new BigDecimal("9000.00")))
			.isInstanceOf(BusinessException.class);
		verify(productSizeRepository, never()).save(any());
	}

	@Test
	void create_duplicateSize_isRejectedAsBusinessCase() {
		// La tabla tiene UNIQUE (product_id, size): sin el chequeo previo esto sería un 500.
		Product product = productWithSizesAllowed(true);
		when(productRepository.findByIdWithCategoryType(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(productSizeRepository.findByProductIdAndSize(PRODUCT_ID, ProductSizeName.CHICA))
			.thenReturn(Optional.of(chicaOf(product)));

		assertThatThrownBy(() ->
			service.create(PRODUCT_ID, ProductSizeName.CHICA, new BigDecimal("9500.00")))
			.isInstanceOf(BusinessException.class);
		verify(productSizeRepository, never()).save(any());
	}

	@Test
	void create_productNotFound_throws() {
		when(productRepository.findByIdWithCategoryType(PRODUCT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() ->
			service.create(PRODUCT_ID, ProductSizeName.CHICA, new BigDecimal("9000.00")))
			.isInstanceOf(ProductNotFoundException.class);
	}

	// ── update (US-SIZE-04) ─────────────────────────────────────────────────────

	@Test
	void update_changesPriceKeepingActive() {
		ProductSize chica = chicaOf(productWithSizesAllowed(true));
		when(productSizeRepository.findById(SIZE_ID)).thenReturn(Optional.of(chica));
		when(productSizeRepository.save(any(ProductSize.class))).thenAnswer(inv -> inv.getArgument(0));

		ProductSize updated = service.update(PRODUCT_ID, SIZE_ID, new BigDecimal("9900.00"), null);

		assertThat(updated.getPrice()).isEqualByComparingTo("9900.00");
		assertThat(updated.getActive()).isTrue();
	}

	@Test
	void update_deactivatesWithoutDeletingTheRow() {
		// Soft-delete: order_item.product_size_id referencia esta fila en pedidos históricos.
		ProductSize chica = chicaOf(productWithSizesAllowed(true));
		when(productSizeRepository.findById(SIZE_ID)).thenReturn(Optional.of(chica));
		when(productSizeRepository.save(any(ProductSize.class))).thenAnswer(inv -> inv.getArgument(0));

		ProductSize updated = service.update(PRODUCT_ID, SIZE_ID, null, false);

		assertThat(updated.getActive()).isFalse();
		assertThat(updated.getPrice()).isEqualByComparingTo("9000.00");
		verify(productSizeRepository, never()).delete(any());
	}

	@Test
	void update_sizeOfAnotherProduct_isRejected() {
		Product otro = Product.builder().id(99).name("Napolitana").build();
		when(productSizeRepository.findById(SIZE_ID)).thenReturn(Optional.of(chicaOf(otro)));

		assertThatThrownBy(() -> service.update(PRODUCT_ID, SIZE_ID, new BigDecimal("9900.00"), null))
			.isInstanceOf(BusinessException.class);
		verify(productSizeRepository, never()).save(any());
	}

	@Test
	void update_sizeNotFound_throws() {
		when(productSizeRepository.findById(SIZE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(PRODUCT_ID, SIZE_ID, new BigDecimal("9900.00"), null))
			.isInstanceOf(ProductSizeNotFoundException.class);
	}

	// ── updateBranchOverride (US-SIZE-04) ───────────────────────────────────────

	@Test
	void updateBranchOverride_createsTheRowWhenThereIsNone() {
		Product product = productWithSizesAllowed(true);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
		when(productSizeRepository.findById(SIZE_ID)).thenReturn(Optional.of(chicaOf(product)));
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.empty());
		when(branchProductSizeRepository.save(any(BranchProductSize.class)))
			.thenAnswer(inv -> inv.getArgument(0));

		BranchProductSize config = service.updateBranchOverride(
			BRANCH_ID, PRODUCT_ID, SIZE_ID, new BigDecimal("9900.00"));

		assertThat(config.getPriceOverride()).isEqualByComparingTo("9900.00");
		assertThat(config.getBranch().getId()).isEqualTo(BRANCH_ID);
	}

	@Test
	void updateBranchOverride_nullClearsTheOverrideDeletingTheRow() {
		// Sin fila y con fila de override nulo son estados equivalentes (US-SIZE-02): se borra
		// en vez de dejar una fila vacía.
		Product product = productWithSizesAllowed(true);
		BranchProductSize existing = BranchProductSize.builder()
			.branch(activeBranch()).productSize(chicaOf(product))
			.priceOverride(new BigDecimal("9900.00")).build();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
		when(productSizeRepository.findById(SIZE_ID)).thenReturn(Optional.of(chicaOf(product)));
		when(branchProductSizeRepository.findByBranchIdAndProductSizeId(BRANCH_ID, SIZE_ID))
			.thenReturn(Optional.of(existing));

		assertThat(service.updateBranchOverride(BRANCH_ID, PRODUCT_ID, SIZE_ID, null)).isNull();

		verify(branchProductSizeRepository).delete(existing);
		verify(branchProductSizeRepository, never()).save(any());
	}

	@Test
	void updateBranchOverride_inactiveBranch_isRejected() {
		// Mismo guard de escritura que ProductService.updateBranchConfig (US-15-06).
		Branch inactiva = Branch.builder().id(BRANCH_ID).name("Centro").active(false).build();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(inactiva));

		assertThatThrownBy(() ->
			service.updateBranchOverride(BRANCH_ID, PRODUCT_ID, SIZE_ID, new BigDecimal("9900.00")))
			.isInstanceOf(BusinessException.class);
		verify(branchProductSizeRepository, never()).save(any());
	}

	@Test
	void updateBranchOverride_sizeOfAnotherProduct_isRejected() {
		Product otro = Product.builder().id(99).name("Napolitana").build();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
		when(productSizeRepository.findById(SIZE_ID)).thenReturn(Optional.of(chicaOf(otro)));

		assertThatThrownBy(() ->
			service.updateBranchOverride(BRANCH_ID, PRODUCT_ID, SIZE_ID, new BigDecimal("9900.00")))
			.isInstanceOf(BusinessException.class);
		verify(branchProductSizeRepository, never()).save(any());
	}
}
