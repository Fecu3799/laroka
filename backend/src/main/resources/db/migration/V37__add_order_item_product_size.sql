-- V37__add_order_item_product_size.sql - Tamaño elegido por ítem del pedido (US-SIZE-03)
-- Nullable: cuando es NULL el ítem se pidió sin tamaño (comportamiento previo, precio base
-- del producto). Cuando trae valor, unit_price ya viene resuelto con el precio efectivo de
-- ese tamaño en esa sucursal (RN-05: precio congelado al momento del pedido).
--
-- Mutuamente excluyente con second_product_id (mitad y mitad): la regla se valida en
-- OrderService, no como CHECK, para devolver un 422 con mensaje de negocio en vez de un
-- error de constraint.

ALTER TABLE order_item
    ADD COLUMN product_size_id INTEGER;

ALTER TABLE order_item
    ADD CONSTRAINT fk_order_item_product_size
        FOREIGN KEY (product_size_id) REFERENCES product_size(id);

CREATE INDEX idx_order_item_product_size_id ON order_item(product_size_id);
