-- V36__create_branch_product_size.sql - Override de precio por sucursal a nivel tamaño (US-SIZE-02)
-- Mismo patrón que branch_product.price_override, pero apuntando a product_size en vez de
-- a product. El precio efectivo de un tamaño en una sucursal es:
--     branch_product_size.price_override ?? product_size.price
--
-- A diferencia de branch_product, estas filas NO se auto-provisionan al crear un producto o
-- una sucursal: la ausencia de fila es semánticamente idéntica a price_override = NULL (sin
-- override, vale el precio base del tamaño). La fila se crea recién cuando el ADMIN carga un
-- override desde el backoffice (US-SIZE-F-01).

CREATE TABLE branch_product_size (
    branch_id INTEGER NOT NULL,
    product_size_id INTEGER NOT NULL,
    price_override NUMERIC(10, 2),
    PRIMARY KEY (branch_id, product_size_id),
    CONSTRAINT fk_branch_product_size_branch
        FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_branch_product_size_product_size
        FOREIGN KEY (product_size_id) REFERENCES product_size(id)
);

CREATE INDEX idx_branch_product_size_product_size_id ON branch_product_size(product_size_id);
