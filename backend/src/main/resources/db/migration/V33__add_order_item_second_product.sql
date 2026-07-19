-- V33__add_order_item_second_product.sql - Ítem mitad y mitad (US-HH-01)
-- second_product_id nullable: cuando es NULL el ítem es un producto simple (sin cambios
-- de comportamiento). Cuando trae valor, el ítem combina product_id + second_product_id.

ALTER TABLE order_item
    ADD COLUMN second_product_id INTEGER;

ALTER TABLE order_item
    ADD CONSTRAINT fk_order_item_second_product
        FOREIGN KEY (second_product_id) REFERENCES product(id);

CREATE INDEX idx_order_item_second_product_id ON order_item(second_product_id);
