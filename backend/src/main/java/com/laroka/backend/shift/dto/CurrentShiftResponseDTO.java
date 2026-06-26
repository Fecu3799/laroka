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
public class CurrentShiftResponseDTO {

    private boolean active;
    private UUID shiftId;
    private OffsetDateTime openedAt;
    private String openedBy;
    private boolean autoClose;
}
