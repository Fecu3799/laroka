package com.laroka.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByCategoryId(Integer categoryId);
    List<Product> findByPizzeriaId(Integer pizzeriaId);
    List<Product> findByPizzeriaIdAndAvailableTrue(Integer pizzeriaId);
}
