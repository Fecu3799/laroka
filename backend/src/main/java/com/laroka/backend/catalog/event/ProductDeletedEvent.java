package com.laroka.backend.catalog.event;

// Evento de dominio publicado por ProductService.delete(). Su único consumidor es
// MenuCacheEvictionListener, que evicta el cache "menu" en fase AFTER_COMMIT.
public record ProductDeletedEvent(Integer productId) {
}
