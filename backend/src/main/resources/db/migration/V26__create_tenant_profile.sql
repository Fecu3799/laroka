-- V26__create_tenant_profile.sql - Perfil público del negocio (1:1 con tenant)

CREATE TABLE tenant_profile (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    instagram_url VARCHAR(255),
    facebook_url VARCHAR(255),
    whatsapp VARCHAR(50),
    logo_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_profile_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_tenant_profile_tenant_id UNIQUE (tenant_id)
);
