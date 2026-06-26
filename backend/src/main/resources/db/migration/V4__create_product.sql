-- V4__create_product.sql - Catálogo maestro de productos y disponibilidad por sucursal

-- Tabla de productos (catálogo maestro de la pizzería, sin branch_id)
CREATE TABLE product (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL,
    image_url VARCHAR(500),
    available BOOLEAN NOT NULL DEFAULT true,
    category_id INTEGER NOT NULL,
    tenant_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_product_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_product_tenant_id ON product(tenant_id);
CREATE INDEX idx_product_category_id ON product(category_id);

-- Tabla de disponibilidad por sucursal
CREATE TABLE branch_product (
    branch_id  INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    available BOOLEAN NOT NULL DEFAULT true,
    price_override NUMERIC(10, 2),
    PRIMARY KEY (branch_id, product_id),
    CONSTRAINT fk_branch_product_branch  FOREIGN KEY (branch_id)  REFERENCES branch(id),
    CONSTRAINT fk_branch_product_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE INDEX idx_branch_product_product_id ON branch_product(product_id);
