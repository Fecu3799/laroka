package com.laroka.backend.catalog.event;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Los métodos que publican MenuCacheEvictionEvent necesitan @Transactional de verdad (escriben
// más de una vez y deben ser atómicos), así que no pueden usar el criterio del resto del
// catálogo de omitirlo para que @CacheEvict corra después del commit. Y combinar @Transactional
// con @CacheEvict en el mismo método deja el orden entre ambos advisors sin garantía: si la
// evicción corre ANTES del commit, un request concurrente repuebla "menu" leyendo todavía el
// estado viejo (READ COMMITTED) y el cache queda stale justo con lo que se quiso invalidar.
//
// AFTER_COMMIT garantiza que la evicción ocurre recién cuando el commit está confirmado, así
// que cualquier repoblado posterior ya ve el estado nuevo. Si la transacción hace rollback el
// listener no corre, que es lo correcto: no hubo cambio que invalidar.
@Component
@RequiredArgsConstructor
@Slf4j
public class MenuCacheEvictionListener {

	private final CacheManager cacheManager;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMenuCacheEviction(MenuCacheEvictionEvent event) {
		// Evicción total en todos los casos: los tres disparadores (baja de producto, cambio de
		// precio base, baja de categoría) afectan el menú de todas las sucursales del tenant.
		Cache menu = cacheManager.getCache("menu");
		if (menu != null) {
			menu.clear();
			log.debug("Cache 'menu' evictado post-commit por {}", event.source());
		}
	}
}
