package com.pedisur.backend.catalog.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Variante de precio/tamaño de un producto (US-SIZE-01). Un producto sin filas en esta
 * tabla se comporta como hasta ahora: precio único en Product.price.
 *
 * Sin tenant_id propio: el tenant se deriva de product.tenant, mismo criterio que
 * BranchProduct. El precio de esta entidad es el base a nivel tenant; el override por
 * sucursal es alcance de US-SIZE-02.
 */
@Entity
@Table(name = "product_size")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSize {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Enumerated(EnumType.STRING)
	@Column(name = "size", nullable = false, length = 20)
	private ProductSizeName size;

	@Column(name = "price", nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Default
	@Column(name = "active", nullable = false)
	private Boolean active = true;
}
