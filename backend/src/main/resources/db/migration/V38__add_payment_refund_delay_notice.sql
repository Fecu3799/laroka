-- V38__add_payment_refund_delay_notice.sql - Aviso de demora de reembolso (US-17-08)
-- refund_failed_at: momento en que el pago entró en REFUND_FAILED. Lo usa el job
--   de avisos para detectar reembolsos sin resolver hace más de N horas.
-- refund_delay_notified: garantiza que el aviso de demora se envíe una sola vez
--   por pedido (se marca true tras el intento de notificación al cliente).

ALTER TABLE payment ADD COLUMN refund_failed_at TIMESTAMP;
ALTER TABLE payment ADD COLUMN refund_delay_notified BOOLEAN NOT NULL DEFAULT FALSE;
