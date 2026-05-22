package com.laroka.backend.branch.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QrConfigResponseDTO {
    private Integer id;
    private Integer branchId;
    private String mpPosId;
    private String mpQrId;
    private boolean active;
}
