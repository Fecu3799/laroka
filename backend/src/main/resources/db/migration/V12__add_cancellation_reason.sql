-- V12__add_cancellation_reason.sql - Motivo de cancelación en historial de estados

ALTER TABLE order_status_history ADD COLUMN cancellation_reason VARCHAR(500);
