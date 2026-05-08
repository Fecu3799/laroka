-- V1__init.sql - Tabla tenant base del sistema
-- Entidad raíz para soporte multi-comercio desde v1

CREATE TABLE tenant (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insertar tenant inicial
INSERT INTO tenant (name) VALUES ('LaRoka');
