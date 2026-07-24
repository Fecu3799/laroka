package com.pedisur.backend.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.pedisur.backend.catalog.entity.CategoryType;
import com.pedisur.backend.catalog.repository.CategoryTypeRepository;

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
