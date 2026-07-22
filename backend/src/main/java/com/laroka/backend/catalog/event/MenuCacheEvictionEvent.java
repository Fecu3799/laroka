package com.laroka.backend.catalog.event;

/**
 * Pide la invalidación del cache "menu" una vez que la transacción que lo publica commitea.
 * Lo consume MenuCacheEvictionListener en fase AFTER_COMMIT.
 *
 * Es un evento genérico y no uno por operación de dominio (ProductDeleted, CategoryDeleted,
 * ...) porque los tres disparadores actuales hacen exactamente lo mismo: limpiar el menú
 * entero. Tres eventos con tres listeners idénticos serían duplicación sin ningún consumidor
 * que distinga entre ellos. Si algún día aparece un consumidor real de esos hechos de dominio
 * —auditoría, notificaciones— corresponde publicar el evento de dominio ahí y dejar este para
 * lo que es: una invalidación de cache.
 *
 * `source` no altera el comportamiento; identifica al disparador en el log del listener,
 * porque la evicción es total y sin eso no hay forma de saber qué la provocó.
 */
public record MenuCacheEvictionEvent(String source) {

	public static MenuCacheEvictionEvent productDeleted(Integer productId) {
		return new MenuCacheEvictionEvent("product-deleted:" + productId);
	}

	public static MenuCacheEvictionEvent productPriceUpdated(Integer productId) {
		return new MenuCacheEvictionEvent("product-price-updated:" + productId);
	}

	public static MenuCacheEvictionEvent categoryDeleted(Integer categoryId) {
		return new MenuCacheEvictionEvent("category-deleted:" + categoryId);
	}
}
