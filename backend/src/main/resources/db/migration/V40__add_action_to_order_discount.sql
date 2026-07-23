-- V40__add_action_to_order_discount.sql - Revertir un descuento aplicado (US-19-06)
--
-- action distingue una aplicación real (APPLIED) de una reversión (REVERTED). La
-- tabla sigue siendo append-only: revertir NO borra ni muta la fila aplicada, sino
-- que inserta una fila REVERTED con percentage=0, discount_amount=0 y
-- final_total_amount=original_total_amount (el pedido vuelve a su total sin
-- descontar). El "vigente" sigue siendo la fila más reciente por applied_at; si esa
-- fila es REVERTED, el pedido no tiene descuento visible aunque la traza completa
-- (aplicado -> revertido) quede en la tabla.
--
-- Backfill: toda fila existente es una aplicación (la columna nace en US-19-06),
-- así que se marca APPLIED antes de imponer el NOT NULL.

ALTER TABLE order_discount ADD COLUMN action VARCHAR(20);

UPDATE order_discount SET action = 'APPLIED';

ALTER TABLE order_discount ALTER COLUMN action SET NOT NULL;
