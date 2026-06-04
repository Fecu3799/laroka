-- V13__add_payment_link.sql - Link de pago persistido para reintentos

ALTER TABLE payment ADD COLUMN payment_link TEXT;
