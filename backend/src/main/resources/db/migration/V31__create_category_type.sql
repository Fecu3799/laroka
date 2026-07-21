-- V31__create_category_type.sql - Tabla maestra de tipos de categoría (US-CAT-01)
-- DDL puro: el seed inicial se carga manualmente por TablePlus (sin INSERT).

CREATE TABLE category_type (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    allows_half_and_half BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_category_type_name UNIQUE (name)
);
