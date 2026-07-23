package com.laroka.backend.order.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.order.entity.OrderDiscount;

@Repository
public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {

    /**
     * Descuento vigente del pedido: la fila más reciente por {@code appliedAt}.
     * La tabla es append-only, así que "vigente" siempre significa "última aplicada".
     */
    Optional<OrderDiscount> findFirstByOrderIdOrderByAppliedAtDesc(UUID orderId);
}
