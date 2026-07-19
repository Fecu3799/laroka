package com.laroka.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.catalog.entity.CategoryType;
import com.laroka.backend.catalog.repository.CategoryTypeRepository;

@ExtendWith(MockitoExtension.class)
class CategoryTypeServiceTest {

	@Mock
	private CategoryTypeRepository repository;

	@InjectMocks
	private CategoryTypeService service;

	@Test
	void findActive_returnsActiveTypesFromRepository() {
		CategoryType pizza = CategoryType.builder().id(1).name("Pizza").allowsHalfAndHalf(true).active(true).build();
		CategoryType empanada = CategoryType.builder().id(2).name("Empanada").allowsHalfAndHalf(false).active(true).build();
		when(repository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(empanada, pizza));

		List<CategoryType> result = service.findActive();

		assertThat(result).extracting(CategoryType::getName).containsExactly("Empanada", "Pizza");
	}
}
