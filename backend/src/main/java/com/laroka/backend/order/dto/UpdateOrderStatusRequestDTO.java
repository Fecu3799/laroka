package com.laroka.backend.order.dto;

import com.laroka.backend.order.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequestDTO {

    @NotNull(message = "nextStatus es obligatorio")
    private OrderStatus nextStatus;

    private String reason;
}
