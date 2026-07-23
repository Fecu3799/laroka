package com.laroka.backend.order.dto;

import java.math.BigDecimal;

import com.laroka.backend.order.entity.DiscountReason;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyDiscountRequestDTO {

    @NotNull(message = "percentage es obligatorio")
    @DecimalMin(value = "0", message = "percentage no puede ser negativo")
    @DecimalMax(value = "100", message = "percentage no puede superar 100")
    private BigDecimal percentage;

    @NotNull(message = "reason es obligatorio")
    private DiscountReason reason;

    @Size(max = 500, message = "note no puede superar 500 caracteres")
    private String note;
}
