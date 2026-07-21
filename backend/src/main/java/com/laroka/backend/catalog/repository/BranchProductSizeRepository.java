package com.laroka.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.BranchProductSize;
import com.laroka.backend.catalog.entity.BranchProductSizeId;

@Repository
public interface BranchProductSizeRepository
		extends JpaRepository<BranchProductSize, BranchProductSizeId> {

	// US-SIZE-02: override de un tamaño puntual en una sucursal. Optional vacío = sin
	// override, vale el precio base del tamaño.
	Optional<BranchProductSize> findByBranchIdAndProductSizeId(Integer branchId, Integer productSizeId);

	// US-SIZE-F-02: todos los overrides de una sucursal, para resolver los precios del menú
	// sin una query por tamaño.
	List<BranchProductSize> findByBranchId(Integer branchId);
}
