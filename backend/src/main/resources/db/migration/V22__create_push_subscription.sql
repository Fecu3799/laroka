-- V22__create_push_subscription.sql - Suscripciones Web Push de clientes anónimos (US-09-01)

CREATE TABLE push_subscription (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint   TEXT        NOT NULL UNIQUE,
    p256dh     TEXT        NOT NULL,
    auth       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Relación orders -> push_subscription: una orden apunta a la suscripción del
-- dispositivo que la generó. Nullable porque la suscripción puede no existir o
-- ser eliminada. Al eliminar una suscripción, el servicio setea esta columna a
-- NULL en las órdenes vinculadas antes de borrar la fila (baja desacoplada).
ALTER TABLE orders
    ADD COLUMN push_subscription_id UUID;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_push_subscription
        FOREIGN KEY (push_subscription_id) REFERENCES push_subscription(id);

CREATE INDEX idx_orders_push_subscription_id ON orders(push_subscription_id);
