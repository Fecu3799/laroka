package com.laroka.backend.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByCategoryId(Integer categoryId);
    List<Product> findByTenantId(Integer tenantId);
    List<Product> findByTenantIdAndAvailableTrue(Integer tenantId);

    // US-14-05: conteo de productos por categoría en una sola query agregada, sin cargar
    // las colecciones. Las categorías sin productos no aparecen en el resultado.
    @Query("SELECT p.category.id AS categoryId, COUNT(p) AS count FROM Product p GROUP BY p.category.id")
    List<CategoryProductCount> countGroupedByCategory();

    interface CategoryProductCount {
        Integer getCategoryId();
        Long getCount();
    }
}
