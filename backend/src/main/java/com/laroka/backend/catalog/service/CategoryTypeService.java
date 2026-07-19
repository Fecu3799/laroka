package com.laroka.backend.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.laroka.backend.catalog.entity.CategoryType;
import com.laroka.backend.catalog.repository.CategoryTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryTypeService {

	private final CategoryTypeRepository repository;

	// US-CAT-03: tipos activos para el selector del backoffice.
	public List<CategoryType> findActive() {
		return repository.findByActiveTrueOrderByNameAsc();
	}
}
