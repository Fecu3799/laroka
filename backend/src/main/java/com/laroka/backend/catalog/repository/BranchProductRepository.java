package com.laroka.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.BranchProductId;

@Repository
public interface BranchProductRepository extends JpaRepository<BranchProduct, BranchProductId> {
    List<BranchProduct> findByBranchId(Integer branchId);

    // Carga product + category en la misma query: el menú se mapea fuera de la
    // sesión (open-in-view=false) y se cachea, así que las asociaciones lazy
    // deben venir inicializadas para evitar LazyInitializationException.
    @EntityGraph(attributePaths = {"product", "product.category"})
    List<BranchProduct> findByBranchIdAndAvailableTrue(Integer branchId);

    Optional<BranchProduct> findByBranchIdAndProductId(Integer branchId, Integer productId);

    List<BranchProduct> findByProductId(Integer productId);

    // Carga branch + product en la misma query: la config por sucursal se mapea fuera de
    // la sesión (open-in-view=false), así que branch (para branchName) y product (para el
    // precio base) deben venir inicializados para evitar LazyInitializationException.
    @Query("SELECT bp FROM BranchProduct bp "
        + "JOIN FETCH bp.branch JOIN FETCH bp.product "
        + "WHERE bp.product.id = :productId")
    List<BranchProduct> findConfigByProductId(@Param("productId") Integer productId);
}