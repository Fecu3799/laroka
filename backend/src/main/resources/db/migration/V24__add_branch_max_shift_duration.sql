-- V24__add_branch_max_shift_duration.sql - Duración máxima de turno por sucursal (US-11-06)
ALTER TABLE branch
    ADD COLUMN max_shift_duration_minutes INTEGER NOT NULL DEFAULT 720;
