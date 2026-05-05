ALTER TABLE branch
    ADD COLUMN estimated_delivery_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN phone VARCHAR(20) NOT NULL DEFAULT '';

UPDATE branch SET estimated_delivery_minutes = 15, phone = '+542804123456' WHERE name = 'Playa Unión';
UPDATE branch SET estimated_delivery_minutes = 20, phone = '+542804234567' WHERE name = 'Rawson';
UPDATE branch SET estimated_delivery_minutes = 30, phone = '+542804345678' WHERE name = 'Madryn';
