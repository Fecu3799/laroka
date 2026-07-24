package com.pedisur.backend.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.order.entity.OrderItem;
import com.pedisur.backend.order.entity.OrderStatus;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);

    // US-HH-F-02: LEFT JOIN FETCH sobre secondProduct (nullable en ítems simples) para que el
    // mapper lea su nombre sin disparar un lazy load por ítem combinado. Medido con show-sql:
    // sin el fetch son 3 SELECT sobre product en vez de 2. El lazy load resuelve igual (no
    // lanza LazyInitializationException), así que esto es N+1, no una corrección de bug.
    // El mismo criterio aplica a productSize, que el DTO expone como sizeName.
    @Query("SELECT i FROM OrderItem i JOIN FETCH i.product "
        + "LEFT JOIN FETCH i.secondProduct LEFT JOIN FETCH i.productSize "
        + "WHERE i.order.id = :orderId")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") UUID orderId);

    @Query("SELECT oi.product.id, oi.product.name, SUM(oi.quantity) " +
           "FROM OrderItem oi " +
           "WHERE oi.order.shift.id = :shiftId " +
           "AND oi.order.status = :status " +
           "GROUP BY oi.product.id, oi.product.name " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopProductsByShiftId(@Param("shiftId") UUID shiftId,
                                            @Param("status") OrderStatus status,
                                            Pageable pageable);
}
