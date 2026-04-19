-- V4__create_product.sql - Catálogo maestro de productos y disponibilidad por sucursal

-- Categorías seed
INSERT INTO category (name, pizzeria_id) VALUES
    ('Pizzas', 1),
    ('Empanadas', 1),
    ('Bebidas', 1);

-- Tabla de productos (catálogo maestro de la pizzería, sin branch_id)
CREATE TABLE product (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    image_url VARCHAR(500),
    available BOOLEAN NOT NULL DEFAULT true,
    category_id INTEGER NOT NULL,
    pizzeria_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_product_pizzeria FOREIGN KEY (pizzeria_id) REFERENCES pizzeria(id)
);

-- Productos seed
INSERT INTO product (name, description, price, category_id, pizzeria_id) VALUES
    ('Muzzarella',  'Salsa de tomate y muzzarella',                            2800.00, 1, 1),
    ('Napolitana',  'Salsa de tomate, muzzarella, tomate y ajo',               3200.00, 1, 1),
    ('Fugazzeta',   'Muzzarella, cebolla y aceitunas',                          3400.00, 1, 1),
    ('Especial',    'Jamón, morrón, aceitunas y muzzarella',                    3800.00, 1, 1),
    ('Carne',       'Carne vacuna, huevo y aceitunas',                           650.00, 2, 1),
    ('Pollo',       'Pollo desmenuzado, morrón y cebolla',                       650.00, 2, 1),
    ('Verdura',     'Espinaca, ricota y huevo',                                  600.00, 2, 1),
    ('Coca-Cola 1.5L',    'Gaseosa',                                            1200.00, 3, 1),
    ('Agua mineral 500ml','Sin gas',                                              500.00, 3, 1);

-- Tabla de disponibilidad por sucursal
CREATE TABLE branch_product (
    branch_id  INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    available      BOOLEAN        NOT NULL DEFAULT true,
    price_override DECIMAL(10, 2),
    PRIMARY KEY (branch_id, product_id),
    CONSTRAINT fk_branch_product_branch  FOREIGN KEY (branch_id)  REFERENCES branch(id),
    CONSTRAINT fk_branch_product_product FOREIGN KEY (product_id) REFERENCES product(id)
);

-- Seed: todos los productos activos disponibles en las 3 sucursales
INSERT INTO branch_product (branch_id, product_id)
SELECT b.id, p.id
FROM branch b
CROSS JOIN product p
WHERE b.pizzeria_id = 1
  AND p.pizzeria_id = 1
  AND p.available = true;
