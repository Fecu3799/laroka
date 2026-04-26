-- V7__create_order_status_history.sql - Historial de cambios de estado de pedidos

CREATE TABLE order_status_history (
    id          UUID         PRIMARY KEY,
    order_id    UUID         NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30)  NOT NULL,
    changed_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
