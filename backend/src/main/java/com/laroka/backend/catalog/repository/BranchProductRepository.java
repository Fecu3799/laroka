package com.laroka.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.BranchProductId;

@Repository
public interface BranchProductRepository extends JpaRepository<BranchProduct, BranchProductId> {
    List<BranchProduct> findByBranchId(Integer branchId);

    Optional<BranchProduct> findByBranchIdAndProductId(Integer branchId, Integer productId);

    boolean existsByBranchIdAndProductId(Integer branchId, Integer productId);

    // US-15-07: BranchProduct de una sucursal cuyos productId estén en la lista. Los
    // productId sin BranchProduct para esa sucursal simplemente no vienen en el resultado.
    List<BranchProduct> findByBranchIdAndProductIdIn(Integer branchId, List<Integer> productIds);

    // US-15-08: todos los BranchProduct de una sucursal (disponibles y no) con product y
    // category cargados (open-in-view=false), ordenados para agrupar por categoría en el front.
    @Query("SELECT bp FROM BranchProduct bp "
        + "JOIN FETCH bp.product p JOIN FETCH p.category c "
        + "WHERE bp.branch.id = :branchId "
        + "ORDER BY c.name ASC, p.name ASC")
    List<BranchProduct> findByBranchIdWithProductAndCategory(@Param("branchId") Integer branchId);

    List<BranchProduct> findByProductId(Integer productId);

    // Carga branch + product en la misma query: la config por sucursal se mapea fuera de
    // la sesión (open-in-view=false), así que branch (para branchName) y product (para el
    // precio base) deben venir inicializados para evitar LazyInitializationException.
    @Query("SELECT bp FROM BranchProduct bp "
        + "JOIN FETCH bp.branch JOIN FETCH bp.product "
        + "WHERE bp.product.id = :productId")
    List<BranchProduct> findConfigByProductId(@Param("productId") Integer productId);

    // Borrado en bloque de todas las entradas de un producto. Se ejecuta dentro de la
    // transacción del service al eliminar el producto (paso previo al delete del producto).
    @Modifying
    @Query("DELETE FROM BranchProduct bp WHERE bp.product.id = :productId")
    void deleteByProductId(@Param("productId") Integer productId);
}