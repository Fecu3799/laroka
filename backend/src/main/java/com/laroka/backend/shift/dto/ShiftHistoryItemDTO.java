package com.laroka.backend.shift.dto;

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
public class ShiftHistoryItemDTO {

    private UUID shiftId;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private String openedBy;
    private String closedBy;
    private CloseShiftResponseDTO summary;
}
