-- V1__init.sql - Tabla pizzeria base del sistema
-- Entidad raíz para soporte multi-pizzería desde v1

CREATE TABLE pizzeria (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insertar pizzería inicial
INSERT INTO pizzeria (name) VALUES ('LaRoka Playa Unión');
