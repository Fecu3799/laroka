-- V39__create_order_discount.sql - Descuento porcentual manual sobre un pedido (US-19-01)
--
-- Tabla append-only: cada aplicación de descuento inserta una fila nueva y ninguna
-- fila anterior se muta ni se borra. El descuento vigente de un pedido es la fila
-- más reciente por applied_at; las anteriores quedan como traza de auditoría.
--
-- original_total_amount es un snapshot de subtotal + delivery_fee + service_fee al
-- momento de aplicar (no de orders.total_amount, que ya puede venir descontado por
-- una aplicación previa). Por eso el cálculo es reproducible: aplicar el mismo
-- porcentaje dos veces da el mismo final_total_amount.
--
-- reason y note son campos de trazabilidad: por qué se otorgó el descuento y quién
-- lo otorgó (applied_by → staff_user). El enum de motivos se persiste como VARCHAR
-- (@Enumerated(STRING)), igual que el resto de los enums del dominio.

CREATE TABLE order_discount (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    percentage NUMERIC(5, 2) NOT NULL,
    original_total_amount NUMERIC(10, 2) NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL,
    final_total_amount NUMERIC(10, 2) NOT NULL,
    reason VARCHAR(30) NOT NULL,
    note VARCHAR(500),
    applied_by INTEGER NOT NULL,
    applied_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_order_discount_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_discount_applied_by
        FOREIGN KEY (applied_by) REFERENCES staff_user(id),
    CONSTRAINT chk_order_discount_percentage
        CHECK (percentage >= 0 AND percentage <= 100)
);

-- Lectura dominante: "el descuento vigente del pedido X" = ORDER BY applied_at DESC
-- filtrando por order_id.
CREATE INDEX idx_order_discount_order_id_applied_at
    ON order_discount(order_id, applied_at DESC);
