-- V23__add_staff_user_active.sql - Campo active en staff_user (US-11-05)
ALTER TABLE staff_user
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
