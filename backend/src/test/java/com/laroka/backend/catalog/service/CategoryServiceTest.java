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
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.exception.PizzeriaNotFoundException;
import com.laroka.backend.pizzeria.repository.PizzeriaRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private PizzeriaRepository pizzeriaRepository;

	@InjectMocks
	private CategoryService service;

	private Pizzeria pizzeria() {
		return Pizzeria.builder().id(1).name("LaRoka").build();
	}

	private Category category(Pizzeria pizzeria) {
		return Category.builder().id(1).name("Pizzas").pizzeria(pizzeria).build();
	}

	@Test
	void findById_returnsExistingCategory() {
		Category category = category(pizzeria());
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
		Pizzeria p = pizzeria();
		when(categoryRepository.findAll()).thenReturn(List.of(category(p), category(p)));

		List<Category> result = service.findAll();

		assertThat(result).hasSize(2);
	}

	@Test
	void findByPizzeria_validPizzeria_returnsCategories() {
		Pizzeria p = pizzeria();
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryRepository.findByPizzeriaId(1)).thenReturn(List.of(category(p)));

		List<Category> result = service.findByPizzeria(1);

		assertThat(result).hasSize(1);
	}

	@Test
	void findByPizzeria_invalidPizzeria_throwsPizzeriaNotFoundException() {
		when(pizzeriaRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByPizzeria(99))
			.isInstanceOf(PizzeriaNotFoundException.class);
	}

	@Test
	void create_validCategory_savesAndReturns() {
		Pizzeria p = pizzeria();
		Category category = category(p);
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryRepository.save(any(Category.class))).thenReturn(category);

		Category result = service.create(category);

		assertThat(result.getName()).isEqualTo("Pizzas");
		verify(categoryRepository).save(category);
	}

	@Test
	void create_invalidPizzeria_throwsPizzeriaNotFoundException() {
		Category category = Category.builder().id(1).name("Test").pizzeria(Pizzeria.builder().id(99).build()).build();
		when(pizzeriaRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(category))
			.isInstanceOf(PizzeriaNotFoundException.class);
	}

	@Test
	void update_existingCategory_updatesAndReturns() {
		Pizzeria p = pizzeria();
		Category existing = category(p);
		Category updates = Category.builder().name("Empanadas").pizzeria(p).build();
		when(categoryRepository.findById(1)).thenReturn(Optional.of(existing));
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(categoryRepository.save(any(Category.class))).thenReturn(existing);

		Category result = service.update(1, updates);

		assertThat(result.getName()).isEqualTo("Empanadas");
	}

	@Test
	void update_notFound_throwsCategoryNotFoundException() {
		when(categoryRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99, category(pizzeria())))
			.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void delete_existingCategory_deletes() {
		Category category = category(pizzeria());
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
