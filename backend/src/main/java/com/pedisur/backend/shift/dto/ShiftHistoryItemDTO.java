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
public class ShiftHistoryItemDTO {

    private UUID shiftId;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private String openedBy;
    private String closedBy;
    // true si el turno se cerró automáticamente (por duración máxima, sin cierre
    // manual). Derivado de closedBy == null en un turno ya CLOSED (US-16-04).
    private boolean autoClose;
    private CloseShiftResponseDTO summary;
}
