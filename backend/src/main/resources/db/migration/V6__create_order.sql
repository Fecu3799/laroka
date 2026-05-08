-- V6__create_order.sql - Tabla de pedidos y ítems de pedido

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    branch_id INTEGER NOT NULL,
    tenant_id INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    origin VARCHAR(20) NOT NULL,
    subtotal NUMERIC(10, 2) NOT NULL,
    delivery_fee NUMERIC(10, 2) NOT NULL,
    service_fee NUMERIC(10, 2) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_orders_branch_id ON orders(branch_id);
CREATE INDEX idx_orders_tenant_id ON orders(tenant_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);


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

CREATE INDEX idx_order_item_order_id ON order_item(order_id);
CREATE INDEX idx_order_item_product_id ON order_item(product_id);