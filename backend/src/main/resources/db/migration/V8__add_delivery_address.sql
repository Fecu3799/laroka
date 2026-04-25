-- V8__add_delivery_address.sql - Agrega campo de dirección de entrega para pedidos DELIVERY

ALTER TABLE orders ADD COLUMN delivery_address VARCHAR(255);
