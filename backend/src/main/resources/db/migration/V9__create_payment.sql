-- V9__create_payment.sql - Tabla de pagos asociados a pedidos

CREATE TABLE payment (
    id                        UUID         PRIMARY KEY,
    mercadopago_payment_id    VARCHAR(255),
    mercadopago_preference_id VARCHAR(255),
    status                    VARCHAR(20)  NOT NULL,
    method                    VARCHAR(20)  NOT NULL,
    paid_at                   TIMESTAMP,
    order_id                  UUID         NOT NULL,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
