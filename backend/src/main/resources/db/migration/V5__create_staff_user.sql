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

-- TODO: En producción, generar hashes BCrypt con herramientas especializadas (e.g., Java BCryptPasswordEncoder)
-- Passwords de desarrollo local (NUNCA usar en producción):
-- Usuario ADMIN: admin@laroka.com / admin123
-- Usuario STAFF: staff@laroka.com / staff123

INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES
    (
        'Administrador LaRoka',
        'admin@laroka.com',
        '$2a$10$4A1l7kHzvUvbGNH5x0FIQeJ/1J7Fmy7wZvvjHlMY6PFPmDqm8sC9q',
        'ADMIN',
        1
    ),
    (
        'Personal Playa Unión',
        'staff@laroka.com',
        '$2a$10$VkZK4pJ8mNHsLqK1vXxCVeJ.h.P8zU3W5qR9tD2eB3C4kL7m9pS2C',
        'STAFF',
        1
    );
