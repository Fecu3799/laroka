package com.laroka.backend.branch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QrConfigRequestDTO {

    @NotBlank(message = "mpPosId es obligatorio")
    private String mpPosId;

    @NotBlank(message = "mpQrId es obligatorio")
    private String mpQrId;
}
