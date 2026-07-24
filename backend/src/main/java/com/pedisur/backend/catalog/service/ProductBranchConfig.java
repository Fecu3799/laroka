package com.pedisur.backend.catalog.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.pedisur.backend.catalog.entity.BranchProduct;
import com.pedisur.backend.catalog.entity.ProductSize;

/**
 * US-SIZE-F-01: configuración por sucursal de un producto, incluyendo el tamaño.
 *
 * `activeSize` es null cuando el producto no tiene tamaño cargado — que es el caso de todos
 * los productos que no son pizza. `sizeOverridesByBranchId` sólo trae las sucursales que
 * tienen override propio; la ausencia de entrada significa "vale el precio base del tamaño".
 *
 * Se compone en el service, y no en el controller, para que el mapper reciba todo resuelto:
 * las asociaciones son lazy y el mapeo a DTO ocurre fuera de la transacción.
 */
public record ProductBranchConfig(
		List<BranchProduct> branchProducts,
		ProductSize activeSize,
		Map<Integer, BigDecimal> sizeOverridesByBranchId) {
}
