package com.laroka.backend.catalog.event;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

// El borrado de producto necesita atomicidad real (branch_product + product en la misma
// transacción), así que no puede usar el criterio del resto del catálogo de omitir
// @Transactional para que @CacheEvict corra después del commit. Combinar @Transactional y
// @CacheEvict en el mismo método deja el orden entre ambos advisors sin garantía: si la
// evicción corre ANTES del commit, un request concurrente repuebla "menu" leyendo el
// producto todavía vivo (READ COMMITTED) y el cache queda con un producto borrado.
//
// AFTER_COMMIT garantiza que la evicción ocurre recién cuando el commit está confirmado, así
// que cualquier repoblado posterior ya no ve el producto. Si la transacción hace rollback el
// listener no corre, que es lo correcto: no hubo cambio que invalidar.
@Component
@RequiredArgsConstructor
public class MenuCacheEvictionListener {

	private final CacheManager cacheManager;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onProductDeleted(ProductDeletedEvent event) {
		// Evicción total (allEntries): el producto deja de existir para todas las sucursales
		// del tenant, mismo criterio que update y updatePrice.
		Cache menu = cacheManager.getCache("menu");
		if (menu != null) {
			menu.clear();
		}
	}
}
