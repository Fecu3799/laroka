-- V6__create_order.sql - Tabla de pedidos y ítems de pedido

CREATE TABLE orders (
    id          UUID         PRIMARY KEY,
    status      VARCHAR(30)  NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    order_type  VARCHAR(20)  NOT NULL,
    notes       TEXT,
    origin      VARCHAR(20)  NOT NULL,
    branch_id   INTEGER      NOT NULL,
    pizzeria_id INTEGER      NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_branch   FOREIGN KEY (branch_id)   REFERENCES branch(id),
    CONSTRAINT fk_orders_pizzeria FOREIGN KEY (pizzeria_id) REFERENCES pizzeria(id)
);

CREATE TABLE order_item (
    id          UUID           PRIMARY KEY,
    quantity    INTEGER        NOT NULL,
    unit_price  DECIMAL(10, 2) NOT NULL,
    subtotal    DECIMAL(10, 2) NOT NULL,
    order_id    UUID           NOT NULL,
    product_id  INTEGER        NOT NULL,
    CONSTRAINT fk_order_item_order   FOREIGN KEY (order_id)   REFERENCES orders(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id)
);
