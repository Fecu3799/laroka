package com.laroka.backend.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.catalog.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

	/*
	 * US-CAT-03: CategoryMapper.toResponseDTO lee category.categoryType.name para exponerlo,
	 * lo que inicializa la asociación lazy. Con spring.jpa.open-in-view=false ese acceso ocurre
	 * fuera de sesión y lanzaría LazyInitializationException. Por eso toda query que cargue
	 * Category y pueda terminar en el mapper trae el categoryType con @EntityGraph. Es un
	 * ManyToOne (una fila), el costo es mínimo — mismo patrón que Branch.tenant.
	 * (tenant no necesita fetch: el mapper solo lee tenant.getId(), que no inicializa el proxy.)
	 */

	@Override
	@EntityGraph(attributePaths = "categoryType")
	Optional<Category> findById(Integer id);

	List<Category> findByTenantId(Integer tenantId);

	// US-14-05: el listado del backoffice retorna las categorías ordenadas por nombre.
	@EntityGraph(attributePaths = "categoryType")
	List<Category> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = "categoryType")
	List<Category> findByTenantIdOrderByNameAsc(Integer tenantId);

	Category findByNameAndTenantId(String name, Integer tenantId);
}
