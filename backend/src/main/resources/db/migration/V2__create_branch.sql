-- V3__create_branch.sql - Tabla de sucursales asociadas a pizzería

CREATE TABLE branch (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    pizzeria_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_branch_pizzeria FOREIGN KEY (pizzeria_id) REFERENCES pizzeria(id)
);

-- Insertar sucursales iniciales
INSERT INTO branch (name, address, pizzeria_id) VALUES
    ('Playa Unión', 'Av. Roca 123, Playa Unión', 1),
    ('Rawson', 'Calle Principal 456, Rawson', 1),
    ('Madryn', 'Blvd. Brown 789, Puerto Madryn', 1);
