# LaRoka — Datos de seed (carga manual)

Sentencias extraídas de las migraciones Flyway en US-12-01.
Ejecutar en orden después de que Flyway haya corrido todas las migraciones.

---

## 1. Tenant

```sql
INSERT INTO tenant (name, email_domain) VALUES ('LaRoka', 'laroka.com');
```

---

## 2. Sucursales

> `accepting_orders` se setea en `true` para desarrollo. En producción usar el endpoint de backoffice.
> `opening_time`, `closing_time` y `open_days` deben setearse aquí porque los UPDATE de V10
> apuntan a los ids 1/2/3 que no existen cuando corre la migración (el seed se carga después).

```sql
INSERT INTO branch (name, address, tenant_id, delivery_fee, service_fee, estimated_delivery_minutes, phone, accepting_orders, opening_time, closing_time, open_days, max_shift_duration_minutes) VALUES
    ('Playa Unión',   'Centenario 423',        1, 500.00,  200.00, 15, '+542804123456', true, '10:00:00', '23:00:00', 'LUN,MAR,MIE,JUE,VIE,SAB,DOM', 720),
    ('Rawson',        '15 de Septiembre 1-98', 1, 800.00,  250.00, 15, '+542804125435', true, '11:00:00', '23:00:00', 'LUN,MAR,MIE,JUE,VIE,SAB,DOM', 720),
    ('Puerto Madryn', 'Blvd. Brown 78',        1, 1000.00, 300.00, 15, '+542804142354', true, '10:00:00', '22:00:00', 'MAR,MIE,JUE,VIE,SAB,DOM',     720);
```

---

## 3. Categorías

```sql
INSERT INTO category (name, tenant_id) VALUES
    ('Pizzas',    1),
    ('Empanadas', 1),
    ('Bebidas',   1);
```

---

## 4. Productos

> Los `category_id` asumen que Pizzas=1, Empanadas=2, Bebidas=3 (orden de inserción).
> Verificar con `SELECT id, name FROM category` antes de ejecutar.

```sql
INSERT INTO product (name, description, price, category_id, tenant_id) VALUES
    ('Muzzarella',       'Salsa de tomate y muzzarella',              2800.00, 1, 1),
    ('Napolitana',       'Salsa de tomate, muzzarella, tomate y ajo', 3200.00, 1, 1),
    ('Fugazzeta',        'Muzzarella, cebolla y aceitunas',            3400.00, 1, 1),
    ('Especial',         'Jamón, morrón, aceitunas y muzzarella',      3800.00, 1, 1),
    ('Carne',            'Carne vacuna, huevo y aceitunas',             650.00, 2, 1),
    ('Pollo',            'Pollo desmenuzado, morrón y cebolla',         650.00, 2, 1),
    ('Verdura',          'Espinaca, ricota y huevo',                    600.00, 2, 1),
    ('Coca-Cola 1.5L',   'Gaseosa',                                    1200.00, 3, 1),
    ('Agua mineral 500ml','Sin gas',                                     500.00, 3, 1);
```

---

## 5. Disponibilidad de productos por sucursal (branch_product)

Activa todos los productos en todas las sucursales del tenant.

```sql
INSERT INTO branch_product (branch_id, product_id, available)
SELECT b.id, p.id, true
FROM branch b
CROSS JOIN product p
WHERE b.tenant_id = 1
  AND p.tenant_id = 1;
```

---

## 6. Usuarios internos

> Passwords de desarrollo local — **no usar en producción**.
> admin@laroka.com / admin123
> staff@laroka.com / staff123

```sql
INSERT INTO staff_user (name, email, password_hash, role, branch_id, active) VALUES
    (
        'Administrador LaRoka',
        'admin@laroka.com',
        '$2a$10$V.TZKF75cSqUd71u52O9Be38/4C0awMPymY8eNypi.JKJfPZYeM1u',
        'ADMIN',
        1,
        true
    ),
    (
        'Personal Playa Unión',
        'staff@laroka.com',
        '$2a$10$vwQtlM/JVmqXN3EBbCFQvuuMcZYxGW5ZqvC1m5UAVLcK5Br6iMbDW',
        'STAFF',
        1,
        true
    );
```

---

## 7. QR MercadoPago por sucursal

> Valores placeholder para dev — reemplazar con IDs reales de MP en staging/prod.
> `active_payment_id` se omite (nullable, se popula en tiempo de ejecución por MercadoPago).

```sql
INSERT INTO branch_qr (branch_id, mp_pos_id, mp_qr_id, active) VALUES
    (1, 'POS_PLAYA_UNION_DEV', 'QR_PLAYA_UNION_DEV', true),
    (2, 'POS_RAWSON_DEV',      'QR_RAWSON_DEV',      true),
    (3, 'POS_MADRYN_DEV',      'QR_MADRYN_DEV',      true);
```
