package com.laroka.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.entity.ProductSizeName;

@Repository
public interface ProductSizeRepository extends JpaRepository<ProductSize, Integer> {

	// US-SIZE-01: tamaños vigentes de un producto. Los inactivos quedan fuera del menú
	// y del selector del backoffice, pero se conservan por los pedidos históricos.
	List<ProductSize> findByProductIdAndActiveTrue(Integer productId);

	// US-SIZE-01: lookup por (producto, tamaño) — es la clave única de la tabla.
	Optional<ProductSize> findByProductIdAndSize(Integer productId, ProductSizeName size);
}
