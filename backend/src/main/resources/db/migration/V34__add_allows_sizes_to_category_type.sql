-- V34__add_allows_sizes_to_category_type.sql - Flag de tamaños por tipo de categoría (US-SIZE-01)
-- Flag separado de allows_half_and_half: son conceptos independientes. Una categoría puede
-- admitir tamaños sin admitir mitad y mitad, mitad y mitad sin tamaños, ambos, o ninguno.
-- DEFAULT FALSE: los tipos ya cargados quedan sin tamaños hasta que el ADMIN los habilite.

ALTER TABLE category_type
    ADD COLUMN allows_sizes BOOLEAN NOT NULL DEFAULT FALSE;
