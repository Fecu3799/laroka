package com.pedisur.backend.catalog.service;

import java.util.List;
import java.util.Map;

import com.pedisur.backend.catalog.entity.BranchProduct;

/**
 * Valor cacheado del menú de una sucursal (US-SIZE-F-02).
 *
 * Los tamaños viajan acá dentro, y no en un cache aparte, a propósito: el menú tiene seis
 * puntos de evicción en ProductService y un segundo cache obligaría a mantenerlos
 * sincronizados, con riesgo de servir precios de tamaño stale. Con un solo valor cacheado,
 * las evicciones que ya existen alcanzan.
 *
 * `sizesByProductId` sólo trae entradas para los productos que tienen tamaños activos.
 */
public record BranchMenu(
		List<BranchProduct> branchProducts,
		Map<Integer, List<ResolvedProductSize>> sizesByProductId) {
}
