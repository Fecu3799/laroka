package com.laroka.backend.order.dto;

import com.laroka.backend.order.entity.DiscountReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Revertir un descuento (US-19-06). No es un borrado sin registro: exige un motivo
 * (mismo enum que aplicar) y admite una nota opcional. No lleva porcentaje: revertir
 * siempre deja el pedido en su total sin descontar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevertDiscountRequestDTO {

    @NotNull(message = "reason es obligatorio")
    private DiscountReason reason;

    @Size(max = 500, message = "note no puede superar 500 caracteres")
    private String note;
}
