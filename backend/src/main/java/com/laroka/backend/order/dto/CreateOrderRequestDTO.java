package com.laroka.backend.order.dto;

import java.util.List;

import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderRequestDTO {

    @NotNull
    private Integer branchId;

    @NotNull
    private OrderType orderType;

    private String deliveryAddress;

    private String customerName;

    private String customerPhone;

    @NotNull
    @NotEmpty
    @Valid
    private List<OrderItemRequestDTO> items;

    private String notes;

    @NotNull
    private PaymentMethod paymentMethod;
}
