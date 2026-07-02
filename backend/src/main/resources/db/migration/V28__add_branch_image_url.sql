-- V28__add_branch_image_url.sql - Imagen propia de la sucursal (US-15-03)
ALTER TABLE branch
    ADD COLUMN image_url VARCHAR(512);
