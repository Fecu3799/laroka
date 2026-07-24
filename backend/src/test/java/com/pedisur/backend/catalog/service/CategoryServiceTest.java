package com.pedisur.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pedisur.backend.catalog.entity.Category;
import com.pedisur.backend.catalog.entity.CategoryType;
import com.pedisur.backend.catalog.exception.CategoryNotFoundException;
import com.pedisur.backend.catalog.exception.CategoryTypeNotFoundException;
import com.pedisur.backend.catalog.repository.CategoryRepository;
import com.pedisur.backend.catalog.repository.CategoryTypeRepository;
import com.pedisur.backend.catalog.repository.ProductRepository;
import com.pedisur.backend.catalog.repository.ProductRepository.CategoryProductCount;
import com.pedisur.backend.tenant.entity.Tenant;
import com.pedisur.backend.tenant.exception.TenantNotFoundException;
import com.pedisur.backend.tenant.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private CategoryTypeRepository categoryTypeRepository;

	@Mock
	private org.springframework.context.ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private CategoryService service;

	private Tenant tenant() {
		return Tenant.builder().id(1).name("LaRoka").build();
	}

	private CategoryType categoryType() {
		return CategoryType.builder().id(5).name("Pizza").allowsHalfAndHalf(true).active(true).build();
	}

	private Category category(Tenant tenant) {
		return Category.builder().id(1).name("Pizzas").tenant(tenant).categoryType(categoryType()).build();
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
	void findAll_returnsAllCategoriesOrderedByName() {
		Tenant p = tenant();
		when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(category(p), category(p)));

		List<Category> result = service.findAll();

		assertThat(result).hasSize(2);
	}

	@Test
	void findByTenant_validTenant_returnsCategories() {
		Tenant p = tenant();
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryRepository.findByTenantIdOrderByNameAsc(1)).thenReturn(List.of(category(p)));

		List<Category> result = service.findByTenant(1);

		assertThat(result).hasSize(1);
	}

	@Test
	void countProductsByCategory_buildsMapKeyedByCategoryId() {
		CategoryProductCount row = mock(CategoryProductCount.class);
		when(row.getCategoryId()).thenReturn(1);
		when(row.getCount()).thenReturn(3L);
		when(productRepository.countGroupedByCategory()).thenReturn(List.of(row));

		Map<Integer, Long> result = service.countProductsByCategory();

		assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1, 3L));
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
		CategoryType type = categoryType();
		Category category = category(p);
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryTypeRepository.findById(5)).thenReturn(Optional.of(type));
		when(categoryRepository.save(any(Category.class))).thenReturn(category);

		Category result = service.create(category);

		assertThat(result.getName()).isEqualTo("Pizzas");
		assertThat(result.getCategoryType().getId()).isEqualTo(5);
		verify(categoryRepository).save(category);
	}

	@Test
	void create_invalidTenant_throwsTenantNotFoundException() {
		Category category = Category.builder().id(1).name("Test")
			.tenant(Tenant.builder().id(99).build()).categoryType(categoryType()).build();
		when(tenantRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(category))
			.isInstanceOf(TenantNotFoundException.class);
	}

	@Test
	void create_invalidCategoryType_throwsCategoryTypeNotFoundException() {
		Tenant p = tenant();
		Category category = Category.builder().id(1).name("Test").tenant(p)
			.categoryType(CategoryType.builder().id(99).build()).build();
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryTypeRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(category))
			.isInstanceOf(CategoryTypeNotFoundException.class);
	}

	@Test
	void update_existingCategory_updatesAndReturns() {
		Tenant p = tenant();
		CategoryType type = categoryType();
		Category existing = category(p);
		Category updates = Category.builder().name("Empanadas").tenant(p).categoryType(type).build();
		when(categoryRepository.findById(1)).thenReturn(Optional.of(existing));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryTypeRepository.findById(5)).thenReturn(Optional.of(type));
		when(categoryRepository.save(any(Category.class))).thenReturn(existing);

		Category result = service.update(1, updates);

		assertThat(result.getName()).isEqualTo("Empanadas");
	}

	@Test
	void update_invalidCategoryType_throwsCategoryTypeNotFoundException() {
		Tenant p = tenant();
		Category existing = category(p);
		Category updates = Category.builder().name("Empanadas").tenant(p)
			.categoryType(CategoryType.builder().id(99).build()).build();
		when(categoryRepository.findById(1)).thenReturn(Optional.of(existing));
		when(tenantRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryTypeRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(1, updates))
			.isInstanceOf(CategoryTypeNotFoundException.class);
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
		// La evicción del menú viaja por el evento, no por @CacheEvict: el borrado en cascada
		// necesita @Transactional y MenuCacheEvictionListener lo consume en AFTER_COMMIT.
		verify(eventPublisher).publishEvent(
			com.pedisur.backend.catalog.event.MenuCacheEvictionEvent.categoryDeleted(1));
	}

	@Test
	void delete_notFound_throwsCategoryNotFoundException() {
		when(categoryRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99))
			.isInstanceOf(CategoryNotFoundException.class);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}
}
