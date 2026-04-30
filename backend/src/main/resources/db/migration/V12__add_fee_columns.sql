-- V12__add_fee_columns.sql - Cargos de delivery y servicio por sucursal (US-03-11)

ALTER TABLE branch
    ADD COLUMN delivery_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN service_fee  NUMERIC(10,2) NOT NULL DEFAULT 0;

ALTER TABLE orders
    ADD COLUMN subtotal     NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN delivery_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN service_fee  NUMERIC(10,2) NOT NULL DEFAULT 0;

UPDATE branch SET delivery_fee = 500.00, service_fee = 200.00;
