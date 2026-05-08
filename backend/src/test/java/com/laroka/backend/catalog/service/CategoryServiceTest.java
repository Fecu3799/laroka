package com.laroka.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.exception.TenantNotFoundException;
import com.laroka.backend.tenant.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private TenantRepository tenantRepository;

	@InjectMocks
	private CategoryService service;

	private Tenant tenant() {
		return Tenant.builder().id(1).name("LaRoka").build();
	}

	private Category category(Tenant tenant) {
		return Category.builder().id(1).name("Pizzas").tenant(tenant).build();
	}

	@Test
	void findById_returnsExistingCategory() {
		Category category = category(tenant());
		when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

		Category result = service.findById(1);

		assertThat(result.getId()).isEqualTo(1);
		assertThat(result.getName()).isEqualTo("Pizzas");
	}

	@Test
	void findById_notFound_throwsCategoryNotFoundException() {
		when(categoryRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(99))
			.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void findAll_returnsAllCategories() {
		Tenant p = tenant();
		when(categoryRepository.findAll()).thenReturn(List.of(category(p), category(p)));

		List<Category> result = service.findAll();

		assertThat(result).hasSize(2);
	}

	@Test
	void findByTenant_validTenant_returnsCategories() {
		Tenant p = tenant();
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryRepository.findByTenantId(1)).thenReturn(List.of(category(p)));

		List<Category> result = service.findByTenant(1);

		assertThat(result).hasSize(1);
	}

	@Test
	void findByTenant_invalidTenant_throwsTenantNotFoundException() {
		when(tenantRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByTenant(99))
			.isInstanceOf(TenantNotFoundException.class);
	}

	@Test
	void create_validCategory_savesAndReturns() {
		Tenant p = tenant();
		Category category = category(p);
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryRepository.save(any(Category.class))).thenReturn(category);

		Category result = service.create(category);

		assertThat(result.getName()).isEqualTo("Pizzas");
		verify(categoryRepository).save(category);
	}

	@Test
	void create_invalidTenant_throwsTenantNotFoundException() {
		Category category = Category.builder().id(1).name("Test").tenant(Tenant.builder().id(99).build()).build();
		when(tenantRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(category))
			.isInstanceOf(TenantNotFoundException.class);
	}

	@Test
	void update_existingCategory_updatesAndReturns() {
		Tenant p = tenant();
		Category existing = category(p);
		Category updates = Category.builder().name("Empanadas").tenant(p).build();
		when(categoryRepository.findById(1)).thenReturn(Optional.of(existing));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryRepository.save(any(Category.class))).thenReturn(existing);

		Category result = service.update(1, updates);

		assertThat(result.getName()).isEqualTo("Empanadas");
	}

	@Test
	void update_notFound_throwsCategoryNotFoundException() {
		when(categoryRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99, category(tenant())))
			.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void delete_existingCategory_deletes() {
		Category category = category(tenant());
		when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

		service.delete(1);

		verify(categoryRepository).delete(category);
	}

	@Test
	void delete_notFound_throwsCategoryNotFoundException() {
		when(categoryRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99))
			.isInstanceOf(CategoryNotFoundException.class);
	}
}
