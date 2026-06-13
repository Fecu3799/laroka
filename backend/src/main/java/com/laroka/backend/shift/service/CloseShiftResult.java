package com.laroka.backend.shift.service;

import com.laroka.backend.shift.entity.WorkShiftSummary;

// Resultado de cerrar un turno. wasEmpty=true indica que el turno no tuvo
// actividad y fue eliminado (summary == null); se señala con un valor en lugar
// de una excepción para que la transacción confirme el delete antes de responder.
public record CloseShiftResult(WorkShiftSummary summary, boolean wasEmpty) {}
