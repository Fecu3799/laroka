package com.laroka.backend.branch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.branch.entity.Branch;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {

	/*
	 * BranchMapper.toResponseDTO accede a branch.getTenant() para mappearlo, lo que
	 * inicializa la asociación lazy. Para que no falle con LazyInitializationException
	 * fuera de sesión, toda query que cargue Branch y pueda terminar en el mapper trae
	 * el tenant con @EntityGraph. tenant es un ManyToOne (una fila), el costo es mínimo.
	 */

	@Override
	@EntityGraph(attributePaths = "tenant")
	Optional<Branch> findById(Integer id);

	@Override
	@EntityGraph(attributePaths = "tenant")
	List<Branch> findAll();

	@EntityGraph(attributePaths = "tenant")
	List<Branch> findByTenantId(Integer tenantId);

	// US-15-04: el endpoint público del client excluye sucursales inactivas.
	@EntityGraph(attributePaths = "tenant")
	List<Branch> findByTenantIdAndActiveTrue(Integer tenantId);

	Branch findByNameAndTenantId(String name, Integer tenantId);

	boolean existsByIdAndTenantId(Integer id, Integer tenantId);

	@Modifying
	@Query("UPDATE Branch b SET b.acceptingOrders = :value WHERE b.id = :branchId")
	int updateAcceptingOrders(@Param("branchId") Integer branchId, @Param("value") boolean value);
}
