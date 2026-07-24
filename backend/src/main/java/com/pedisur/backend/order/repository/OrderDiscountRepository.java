package com.pedisur.backend.order.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.order.entity.OrderDiscount;

@Repository
public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {

    /**
     * Descuento vigente del pedido: la fila más reciente por {@code appliedAt}.
     * La tabla es append-only, así que "vigente" siempre significa "última aplicada".
     */
    Optional<OrderDiscount> findFirstByOrderIdOrderByAppliedAtDesc(UUID orderId);

    /**
     * Descuentos de varios pedidos en una sola query (US-19-04), para que la lista del
     * backoffice no dispare un SELECT por pedido en cada refresco. Vienen ordenados
     * por {@code appliedAt} DESC: al agrupar por pedido, el primero es el vigente.
     */
    List<OrderDiscount> findByOrderIdInOrderByAppliedAtDesc(Collection<UUID> orderIds);
}
