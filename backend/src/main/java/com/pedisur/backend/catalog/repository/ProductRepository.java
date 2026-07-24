package com.pedisur.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.catalog.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByCategoryId(Integer categoryId);
    List<Product> findByTenantId(Integer tenantId);
    List<Product> findByTenantIdAndAvailableTrue(Integer tenantId);

    // US-HH-02: la validación mitad y mitad en OrderService lee
    // product.category.categoryType.allowsHalfAndHalf. Trae ese grafo en un solo fetch para
    // no depender del lazy loading (evita N+1 y cualquier acceso a un proxy no inicializado).
    @EntityGraph(attributePaths = {"category", "category.categoryType"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithCategoryType(@Param("id") Integer id);

    // US-14-05: conteo de productos por categoría en una sola query agregada, sin cargar
    // las colecciones. Las categorías sin productos no aparecen en el resultado.
    @Query("SELECT p.category.id AS categoryId, COUNT(p) AS count FROM Product p GROUP BY p.category.id")
    List<CategoryProductCount> countGroupedByCategory();

    interface CategoryProductCount {
        Integer getCategoryId();
        Long getCount();
    }
}
