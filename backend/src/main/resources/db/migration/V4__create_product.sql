-- V4__create_product.sql - Tabla de productos

CREATE TABLE product (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    image_url VARCHAR(500),
    available BOOLEAN NOT NULL DEFAULT true,
    category_id INTEGER NOT NULL,
    branch_id INTEGER NOT NULL,
    pizzeria_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_product_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_product_pizzeria FOREIGN KEY (pizzeria_id) REFERENCES pizzeria(id)
);
