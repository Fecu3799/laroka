-- V2__create_branch.sql - Tabla de sucursales asociadas a pizzería

CREATE TABLE branch (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 0,
    service_fee NUMERIC(10, 2) NOT NULL DEFAULT 0,
    estimated_delivery_minutes INTEGER NOT NULL DEFAULT 30,
    phone VARCHAR(20) NOT NULL DEFAULT '',
    tenant_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_branch_tenant_id ON branch(tenant_id);

-- Insertar sucursales iniciales
INSERT INTO branch (name, address, tenant_id, delivery_fee, service_fee, estimated_delivery_minutes, phone) VALUES
    ('Playa Unión', 'Centenario 423', 1, 500.00, 200.00, 15, '+542804123456'),
    ('Rawson', '15 de Septiembre 1-98', 1, 800.00, 250.00, 15, '+542804125435'),
    ('Puerto Madryn', 'Blvd. Brown 78', 1, 1000.00, 300.00, 15, '+542804142354');