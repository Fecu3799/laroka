package com.laroka.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.CategoryType;

@Repository
public interface CategoryTypeRepository extends JpaRepository<CategoryType, Integer> {

	// US-CAT-03: el selector del backoffice solo ofrece los tipos activos, ordenados por nombre.
	List<CategoryType> findByActiveTrueOrderByNameAsc();
}
