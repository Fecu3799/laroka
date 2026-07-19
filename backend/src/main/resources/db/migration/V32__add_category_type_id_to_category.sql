-- V32__add_category_type_id_to_category.sql - FK category → category_type (US-CAT-02)
-- Nullable inicialmente: sin migración de datos automática. Las categorías existentes
-- quedan con category_type_id = NULL hasta que el ADMIN las reasigne manualmente desde
-- el backoffice tras cargar el seed (ver docs/KNOWN_ISSUES.md).

ALTER TABLE category
    ADD COLUMN category_type_id INTEGER;

ALTER TABLE category
    ADD CONSTRAINT fk_category_category_type
        FOREIGN KEY (category_type_id) REFERENCES category_type(id);

CREATE INDEX idx_category_category_type_id ON category(category_type_id);
