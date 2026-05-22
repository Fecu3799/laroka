package com.laroka.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequestActionDTO {

    @NotBlank(message = "action es obligatorio")
    private String action;
}
