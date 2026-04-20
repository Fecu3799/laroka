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
        '$2a$10$V.TZKF75cSqUd71u52O9Be38/4C0awMPymY8eNypi.JKJfPZYeM1u',
        'ADMIN',
        1
    ),
    (
        'Personal Playa Unión',
        'staff@laroka.com',
        '$2a$10$vwQtlM/JVmqXN3EBbCFQvuuMcZYxGW5ZqvC1m5UAVLcK5Br6iMbDW',
        'STAFF',
        1
    );
