package com.pedisur.backend.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tipo de categoría maestro (US-CAT-01 / US-CAT-02). Catálogo global del que el ADMIN
 * elige al crear categorías. El seed se carga manualmente por TablePlus.
 */
@Entity
@Table(name = "category_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "allows_half_and_half", nullable = false)
	private boolean allowsHalfAndHalf;

	// US-SIZE-01: independiente de allowsHalfAndHalf. Una categoría puede admitir tamaños
	// sin admitir mitad y mitad, mitad y mitad sin tamaños, ambos, o ninguno.
	@Column(name = "allows_sizes", nullable = false)
	private boolean allowsSizes;

	@Column(name = "active", nullable = false)
	private boolean active;
}
