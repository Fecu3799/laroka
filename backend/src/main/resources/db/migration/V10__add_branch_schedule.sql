-- V10__add_branch_schedule.sql - Horario operativo por sucursal

ALTER TABLE branch ADD COLUMN opening_time TIME NOT NULL DEFAULT '10:00:00';
ALTER TABLE branch ADD COLUMN closing_time TIME NOT NULL DEFAULT '23:00:00';
ALTER TABLE branch ADD COLUMN open_days VARCHAR(50) NOT NULL DEFAULT 'LUN,MAR,MIE,JUE,VIE,SAB,DOM';

UPDATE branch SET opening_time = '10:00:00', closing_time = '23:00:00', open_days = 'LUN,MAR,MIE,JUE,VIE,SAB,DOM' WHERE id = 1;
UPDATE branch SET opening_time = '11:00:00', closing_time = '23:00:00', open_days = 'LUN,MAR,MIE,JUE,VIE,SAB,DOM' WHERE id = 2;
UPDATE branch SET opening_time = '10:00:00', closing_time = '22:00:00', open_days = 'MAR,MIE,JUE,VIE,SAB,DOM'     WHERE id = 3;
