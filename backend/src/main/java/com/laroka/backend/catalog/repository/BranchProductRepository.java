package com.laroka.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}