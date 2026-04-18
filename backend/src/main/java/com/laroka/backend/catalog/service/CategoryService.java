package com.laroka.backend.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.exception.PizzeriaNotFoundException;
import com.laroka.backend.pizzeria.repository.PizzeriaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final CategoryRepository repository;
	private final PizzeriaRepository pizzeriaRepository;

	public Category findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new CategoryNotFoundException(id));
	}

	public List<Category> findByPizzeria(Integer pizzeriaId) {
		validatePizzeriaExists(pizzeriaId);
		return repository.findByPizzeriaId(pizzeriaId);
	}

	public List<Category> findAll() {
		return repository.findAll();
	}

	public Category create(Category category) {
		Pizzeria pizzeria = validatePizzeriaExists(category.getPizzeria().getId());
		category.setPizzeria(pizzeria);
		return repository.save(category);
	}

	public Category update(Integer id, Category updates) {
		Category category = findById(id);
		Pizzeria pizzeria = validatePizzeriaExists(updates.getPizzeria().getId());
		category.setName(updates.getName());
		category.setPizzeria(pizzeria);
		return repository.save(category);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}

	private Pizzeria validatePizzeriaExists(Integer pizzeriaId) {
		return pizzeriaRepository.findById(pizzeriaId)
			.orElseThrow(() -> new PizzeriaNotFoundException(pizzeriaId));
	}
}
