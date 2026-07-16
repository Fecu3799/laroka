-- V30__add_payment_refunded_amount.sql - Tracking de reembolsos (US-17-04)
-- Monto reembolsado (total = totalAmount del pedido; parcial = 85% del subtotal).
-- nullable: null = sin reembolso. El estado del reembolso (éxito/fallo) se modela
-- con PaymentStatus (REFUNDED / REFUND_FAILED), no requiere columna adicional.

ALTER TABLE payment ADD COLUMN refunded_amount NUMERIC(10, 2);
