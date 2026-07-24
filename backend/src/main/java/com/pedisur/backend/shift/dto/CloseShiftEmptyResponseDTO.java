package com.pedisur.backend.shift.dto;

// Respuesta 200 del cierre de un turno sin actividad: el turno fue eliminado y
// no hay resumen que devolver. El frontend distingue este caso por empty=true.
public record CloseShiftEmptyResponseDTO(boolean empty, String message) {}
