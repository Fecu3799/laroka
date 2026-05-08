-- V3_create_category.sql - Tabla de categorías de productos

CREATE TABLE category (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tenant_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uk_category_name_tenant UNIQUE (name, tenant_id)
);

CREATE INDEX idx_category_tenant_id ON category(tenant_id);

-- Categorías seed
INSERT INTO category (name, tenant_id) VALUES
    ('Pizzas', 1),
    ('Empanadas', 1),
    ('Bebidas', 1);