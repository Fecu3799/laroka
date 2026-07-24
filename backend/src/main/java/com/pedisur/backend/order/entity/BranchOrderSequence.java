package com.pedisur.backend.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contador del número de orden secuencial por sucursal (US-16B-03).
 *
 * Una fila por sucursal. El incremento atómico no se hace mutando esta entidad
 * (eso abriría una condición de carrera lectura-escritura), sino con un
 * UPSERT ... RETURNING nativo en BranchOrderSequenceRepository. La entidad existe
 * para que ddl-auto=validate reconozca la tabla branch_order_sequence.
 */
@Entity
@Table(name = "branch_order_sequence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchOrderSequence {

    @Id
    @Column(name = "branch_id")
    private Integer branchId;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;
}
