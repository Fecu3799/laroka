-- V5__create_staff_user.sql - Tabla de usuarios internos del sistema

CREATE TABLE staff_user (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    branch_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_staff_user_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
);

CREATE INDEX idx_staff_user_branch_id ON staff_user(branch_id);
