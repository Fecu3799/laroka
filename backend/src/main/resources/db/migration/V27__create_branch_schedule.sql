-- V27__create_branch_schedule.sql - Nuevo modelo de horarios por día de la semana
-- con soporte de dos franjas diarias y días especiales (overrides).
-- Elimina el modelo viejo (opening_time/closing_time/open_days) de branch.
-- No inserta datos: los horarios se cargan manualmente tras el reset de staging.

CREATE TABLE branch_schedule (
    id SERIAL PRIMARY KEY,
    branch_id INTEGER NOT NULL,
    day_of_week VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    open_time TIME,
    close_time TIME,
    open_time_2 TIME,
    close_time_2 TIME,
    CONSTRAINT fk_branch_schedule_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT uq_branch_schedule_branch_day UNIQUE (branch_id, day_of_week)
);

CREATE TABLE branch_schedule_override (
    id SERIAL PRIMARY KEY,
    branch_id INTEGER NOT NULL,
    date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    open_time TIME,
    close_time TIME,
    open_time_2 TIME,
    close_time_2 TIME,
    reason VARCHAR(255),
    priority INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_branch_schedule_override_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT uq_branch_schedule_override_branch_date UNIQUE (branch_id, date)
);

ALTER TABLE branch DROP COLUMN opening_time;
ALTER TABLE branch DROP COLUMN closing_time;
ALTER TABLE branch DROP COLUMN open_days;
