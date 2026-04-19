package com.laroka.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.BranchProductId;

@Repository
public interface BranchProductRepository extends JpaRepository<BranchProduct, BranchProductId> {
    List<BranchProduct> findByBranchId(Integer branchId);
    List<BranchProduct> findByBranchIdAndAvailableTrue(Integer branchId);
    Optional<BranchProduct> findByBranchIdAndProductId(Integer branchId, Integer productId);
}
