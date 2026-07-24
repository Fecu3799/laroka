package com.pedisur.backend.catalog.entity;

import java.math.BigDecimal;

import com.pedisur.backend.branch.entity.Branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "branch_product")
@IdClass(BranchProductId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProduct {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Default
    @Column(name = "available", nullable = false)
    private Boolean available = true;

    @Column(name = "price_override", precision = 10, scale = 2)
    private BigDecimal priceOverride;
}
