-- V11__create_branch_qr.sql - QR de MercadoPago por sucursal

CREATE TABLE branch_qr (
    id SERIAL PRIMARY KEY,
    branch_id INTEGER NOT NULL,
    mp_pos_id VARCHAR(255) NOT NULL,
    mp_qr_id VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_branch_qr_branch UNIQUE (branch_id),
    CONSTRAINT fk_branch_qr_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
);

CREATE INDEX idx_branch_qr_branch_id ON branch_qr(branch_id);
