-- V29__add_order_sequential_number.sql
-- Número de orden secuencial, continuo y por sucursal (US-16B-03).
--
-- Reemplaza, sólo a nivel de presentación, el UUID truncado del ticket por un
-- número legible tipo "Orden #47". El UUID sigue siendo el id interno del pedido.
--
-- El contador vive en branch_order_sequence (una fila por sucursal) y se
-- incrementa de forma atómica con un UPSERT ... RETURNING dentro de la misma
-- transacción de creación del pedido: el lock de fila serializa los pedidos
-- concurrentes de una misma sucursal, garantizando unicidad sin reintentos.

-- Contador por sucursal.
CREATE TABLE branch_order_sequence (
    branch_id  INTEGER PRIMARY KEY,
    next_value BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT fk_branch_order_sequence_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
);

-- Número visible del pedido (nullable en este paso: se backfillea abajo).
ALTER TABLE orders ADD COLUMN order_number BIGINT;

-- Backfill de pedidos existentes: numeración continua por sucursal en orden de
-- creación, para que el histórico también muestre "Orden #N".
UPDATE orders o
SET order_number = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY branch_id ORDER BY created_at, id) AS rn
    FROM orders
) sub
WHERE o.id = sub.id;

-- Seed del contador con el máximo ya usado por sucursal. Las sucursales sin
-- pedidos no reciben fila: su primer pedido la crea vía UPSERT arrancando en 1.
INSERT INTO branch_order_sequence (branch_id, next_value)
SELECT branch_id, MAX(order_number)
FROM orders
GROUP BY branch_id;

-- A partir de acá todo pedido nuevo trae número asignado en la creación.
ALTER TABLE orders ALTER COLUMN order_number SET NOT NULL;

-- Red de seguridad a nivel DB: dos pedidos de la misma sucursal nunca comparten
-- número, aunque falle la lógica de aplicación.
ALTER TABLE orders ADD CONSTRAINT uq_orders_branch_order_number UNIQUE (branch_id, order_number);
