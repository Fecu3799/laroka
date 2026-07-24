package com.pedisur.backend.shift.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenShiftResponseDTO {

    private UUID shiftId;
    private OffsetDateTime openedAt;
    private Integer branchId;
    private boolean warningPreviousShiftClosed;
}
