-- V4__create_category.sql - Tabla de categorías de productos

CREATE TABLE category (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    pizzeria_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_pizzeria FOREIGN KEY (pizzeria_id) REFERENCES pizzeria(id),
    CONSTRAINT uk_category_name_pizzeria UNIQUE (name, pizzeria_id)
);
