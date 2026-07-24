package com.pedisur.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.catalog.entity.ProductSize;
import com.pedisur.backend.catalog.entity.ProductSizeName;

@Repository
public interface ProductSizeRepository extends JpaRepository<ProductSize, Integer> {

	// US-SIZE-01: tamaños vigentes de un producto. Los inactivos quedan fuera del menú
	// y del selector del backoffice, pero se conservan por los pedidos históricos.
	List<ProductSize> findByProductIdAndActiveTrue(Integer productId);

	// US-SIZE-04: todos los tamaños de un producto para el backoffice, activos e inactivos —
	// el ADMIN tiene que ver el que dio de baja para poder reactivarlo.
	List<ProductSize> findByProductIdOrderByIdAsc(Integer productId);

	// US-SIZE-01: lookup por (producto, tamaño) — es la clave única de la tabla.
	Optional<ProductSize> findByProductIdAndSize(Integer productId, ProductSizeName size);

	// US-SIZE-F-02: tamaños activos de todos los productos ofrecidos en una sucursal, en una
	// sola query. Alimenta el menú; sin esto habría un SELECT por producto.
	@Query("SELECT ps FROM ProductSize ps WHERE ps.active = true AND ps.product.id IN "
		+ "(SELECT bp.product.id FROM BranchProduct bp WHERE bp.branch.id = :branchId) "
		+ "ORDER BY ps.id ASC")
	List<ProductSize> findActiveByBranchId(@Param("branchId") Integer branchId);
}
