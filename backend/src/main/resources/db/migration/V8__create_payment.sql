-- V9__create_payment.sql - Tabla de pagos asociados a pedidos

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    mercadopago_payment_id VARCHAR(255) NOT NULL,
    mercadopago_preference_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    method VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_payment_order_id ON payment(order_id);