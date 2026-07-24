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
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Override de precio por sucursal a nivel tamaño (US-SIZE-02). Análogo a BranchProduct,
 * pero apuntando a ProductSize en vez de a Product.
 *
 * No lleva `available`: la disponibilidad sigue siendo del producto base (BranchProduct).
 * Tampoco se auto-provisiona — sin fila, el tamaño vale su precio base.
 */
@Entity
@Table(name = "branch_product_size")
@IdClass(BranchProductSizeId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProductSize {

	@Id
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Id
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_size_id", nullable = false)
	private ProductSize productSize;

	@Column(name = "price_override", precision = 10, scale = 2)
	private BigDecimal priceOverride;
}
