package com.pedisur.backend.catalog.service;

import java.math.BigDecimal;

import com.pedisur.backend.catalog.entity.ProductSizeName;

/**
 * Tamaño de un producto con el precio ya resuelto para una sucursal (US-SIZE-F-02):
 * branch_product_size.price_override ?? product_size.price (US-SIZE-02).
 *
 * Viaja dentro del valor cacheado del menú para que el mapper no tenga que resolver nada
 * fuera de la transacción.
 */
public record ResolvedProductSize(Integer id, ProductSizeName size, BigDecimal price) {
}
