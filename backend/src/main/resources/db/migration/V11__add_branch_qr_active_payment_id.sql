-- V11__add_branch_qr_active_payment_id.sql
-- Stores the external_id of the active QR charge in MercadoPago (RN-21)

ALTER TABLE branch_qr ADD COLUMN active_payment_id VARCHAR(255);
