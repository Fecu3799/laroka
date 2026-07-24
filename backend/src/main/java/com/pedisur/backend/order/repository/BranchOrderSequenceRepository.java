package com.pedisur.backend.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pedisur.backend.order.entity.BranchOrderSequence;

public interface BranchOrderSequenceRepository extends JpaRepository<BranchOrderSequence, Integer> {

    /**
     * Devuelve el próximo número de orden para la sucursal de forma atómica.
     *
     * El UPSERT crea la fila del contador en 1 la primera vez, o incrementa el
     * valor existente en +1. El ON CONFLICT DO UPDATE toma un lock de fila que se
     * mantiene hasta el commit de la transacción de creación del pedido, de modo
     * que dos pedidos concurrentes de la misma sucursal quedan serializados y
     * nunca reciben el mismo número. Sucursales distintas tocan filas distintas y
     * no contienden.
     *
     * El UPSERT se envuelve en un CTE con un SELECT externo para que Hibernate lo
     * trate como consulta (con resultado) y no como DML, evitando @Modifying.
     */
    @Query(value = """
            WITH upsert AS (
                INSERT INTO branch_order_sequence (branch_id, next_value)
                VALUES (:branchId, 1)
                ON CONFLICT (branch_id)
                DO UPDATE SET next_value = branch_order_sequence.next_value + 1
                RETURNING next_value
            )
            SELECT next_value FROM upsert
            """, nativeQuery = true)
    Long nextOrderNumber(@Param("branchId") Integer branchId);
}
