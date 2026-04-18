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
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.exception.ProductNotFoundException;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.exception.PizzeriaNotFoundException;
import com.laroka.backend.pizzeria.repository.PizzeriaRepository;
import com.laroka.backend.shared.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private BranchRepository branchRepository;

	@Mock
	private PizzeriaRepository pizzeriaRepository;

	@InjectMocks
	private ProductService service;

	private Pizzeria pizzeria() {
		return Pizzeria.builder().id(1).name("LaRoka").build();
	}

	private Branch branch(Pizzeria pizzeria) {
		return Branch.builder().id(1).name("Playa Unión").address("Av. Principal 123").pizzeria(pizzeria).build();
	}

	private Category category(Pizzeria pizzeria) {
		return Category.builder().id(1).name("Pizzas").pizzeria(pizzeria).build();
	}

	private Product product(Branch branch, Category category, Pizzeria pizzeria) {
		return Product.builder()
			.id(1)
			.name("Muzzarella")
			.description("Clásica")
			.price(new BigDecimal("1500.00"))
			.available(true)
			.branch(branch)
			.category(category)
			.pizzeria(pizzeria)
			.build();
	}

	@Test
	void findById_returnsExistingProduct() {
		Pizzeria p = pizzeria();
		Product product = product(branch(p), category(p), p);
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

	@Test
	void findByBranch_validBranch_returnsProducts() {
		Pizzeria p = pizzeria();
		Branch b = branch(p);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(productRepository.findByBranchId(1)).thenReturn(List.of(product(b, category(p), p)));

		List<Product> result = service.findByBranch(1);

		assertThat(result).hasSize(1);
	}

	@Test
	void findByBranch_invalidBranch_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByBranch(99))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void findAvailableByBranch_returnsOnlyAvailableProducts() {
		Pizzeria p = pizzeria();
		Branch b = branch(p);
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(productRepository.findByBranchIdAndAvailableTrue(1)).thenReturn(List.of(product(b, category(p), p)));

		List<Product> result = service.findAvailableByBranch(1);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getAvailable()).isTrue();
	}

	@Test
	void findByCategory_invalidCategory_throwsCategoryNotFoundException() {
		when(categoryRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByCategory(99))
			.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void findByPizzeria_invalidPizzeria_throwsPizzeriaNotFoundException() {
		when(pizzeriaRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByPizzeria(99))
			.isInstanceOf(PizzeriaNotFoundException.class);
	}

	@Test
	void create_validProduct_savesAndReturns() {
		Pizzeria p = pizzeria();
		Branch b = branch(p);
		Category c = category(p);
		Product product = product(b, c, p);
		when(categoryRepository.findById(1)).thenReturn(Optional.of(c));
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(productRepository.save(any(Product.class))).thenReturn(product);

		Product result = service.create(product);

		assertThat(result.getName()).isEqualTo("Muzzarella");
		verify(productRepository).save(product);
	}

	@Test
	void create_invalidCategory_throwsCategoryNotFoundException() {
		Pizzeria p = pizzeria();
		Product product = product(branch(p), category(p), p);
		when(categoryRepository.findById(1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(product))
			.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void create_invalidBranch_throwsBranchNotFoundException() {
		Pizzeria p = pizzeria();
		Product product = product(branch(p), category(p), p);
		when(categoryRepository.findById(1)).thenReturn(Optional.of(category(p)));
		when(branchRepository.findById(1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(product))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void create_invalidPizzeria_throwsPizzeriaNotFoundException() {
		Pizzeria p = pizzeria();
		Product product = product(branch(p), category(p), p);
		when(categoryRepository.findById(1)).thenReturn(Optional.of(category(p)));
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch(p)));
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(product))
			.isInstanceOf(PizzeriaNotFoundException.class);
	}

	@Test
	void update_existingProduct_updatesAndReturns() {
		Pizzeria p = pizzeria();
		Branch b = branch(p);
		Category c = category(p);
		Product existing = product(b, c, p);
		Product updates = Product.builder()
			.name("Fugazzeta")
			.description("Con cebolla")
			.price(new BigDecimal("1800.00"))
			.available(true)
			.branch(b)
			.category(c)
			.pizzeria(p)
			.build();
		when(productRepository.findById(1)).thenReturn(Optional.of(existing));
		when(categoryRepository.findById(1)).thenReturn(Optional.of(c));
		when(branchRepository.findById(1)).thenReturn(Optional.of(b));
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(productRepository.save(any(Product.class))).thenReturn(existing);

		Product result = service.update(1, updates);

		assertThat(result.getName()).isEqualTo("Fugazzeta");
	}

	@Test
	void update_notFound_throwsProductNotFoundException() {
		when(productRepository.findById(99)).thenReturn(Optional.empty());
		Pizzeria p = pizzeria();

		assertThatThrownBy(() -> service.update(99, product(branch(p), category(p), p)))
			.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void delete_existingProduct_deletes() {
		Pizzeria p = pizzeria();
		Product product = product(branch(p), category(p), p);
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

	@Test
	void updateAvailability_sameUserBranch_updatesAvailability() {
		Pizzeria p = pizzeria();
		Branch b = branch(p);
		Product product = product(b, category(p), p);
		when(productRepository.findById(1)).thenReturn(Optional.of(product));
		when(productRepository.save(any(Product.class))).thenReturn(product);

		Product result = service.updateAvailability(1, false, 1);

		assertThat(result.getAvailable()).isFalse();
		verify(productRepository).save(product);
	}

	@Test
	void updateAvailability_differentBranch_throwsBusinessException() {
		Pizzeria p = pizzeria();
		Product product = product(branch(p), category(p), p);
		when(productRepository.findById(1)).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> service.updateAvailability(1, false, 99))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("branch");
	}

	@Test
	void updateAvailability_nullUserBranch_updatesWithoutBranchValidation() {
		Pizzeria p = pizzeria();
		Product product = product(branch(p), category(p), p);
		when(productRepository.findById(1)).thenReturn(Optional.of(product));
		when(productRepository.save(any(Product.class))).thenReturn(product);

		Product result = service.updateAvailability(1, false, null);

		assertThat(result.getAvailable()).isFalse();
	}
}
