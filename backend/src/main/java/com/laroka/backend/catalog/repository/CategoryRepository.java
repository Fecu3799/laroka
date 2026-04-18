package com.laroka.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
	List<Category> findByPizzeriaId(Integer pizzeriaId);

	Category findByNameAndPizzeriaId(String name, Integer pizzeriaId);
}
