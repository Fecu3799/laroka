-- V35__create_product_size.sql - Tamaños de producto con precio propio (US-SIZE-01)
-- DDL puro: las filas las carga el ADMIN desde el backoffice (US-SIZE-F-01), sin seed.
--
-- El precio de esta tabla es el precio base del tamaño a nivel tenant. El override por
-- sucursal es alcance de US-SIZE-02 (tabla branch_product_size), no de esta migración.
--
-- La restricción "solo productos de una categoría con allows_sizes pueden tener tamaños"
-- no es expresable como constraint declarativo (requiere atravesar product → category →
-- category_type). Se valida en el service; ver docs/DEUDA_TECNICA.md.

CREATE TABLE product_size (
    id SERIAL PRIMARY KEY,
    product_id INTEGER NOT NULL,
    size VARCHAR(20) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_product_size_product
        FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT uk_product_size_product_size UNIQUE (product_id, size)
);

CREATE INDEX idx_product_size_product_id ON product_size(product_id);
