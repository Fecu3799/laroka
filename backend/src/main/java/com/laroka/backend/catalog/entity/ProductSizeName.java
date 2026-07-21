package com.laroka.backend.catalog.entity;

/**
 * Tamaños disponibles para un producto (US-SIZE-01). Conjunto cerrado: el ADMIN elige
 * de esta lista al cargar tamaños, no escribe texto libre. Persistido como VARCHAR por
 * nombre (EnumType.STRING), igual que el resto de los enums del dominio.
 */
public enum ProductSizeName {
	CHICA,
	GRANDE
}
