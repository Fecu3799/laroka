package com.laroka.backend.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderStatus;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);

    @Query("SELECT i FROM OrderItem i JOIN FETCH i.product WHERE i.order.id = :orderId")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") UUID orderId);

    @Query("SELECT oi.product.id, oi.product.name, SUM(oi.quantity) " +
           "FROM OrderItem oi " +
           "WHERE oi.order.branch.id = :branchId " +
           "AND oi.order.status = :status " +
           "AND oi.order.createdAt BETWEEN :from AND :to " +
           "GROUP BY oi.product.id, oi.product.name " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopProducts(@Param("branchId") Integer branchId,
                                   @Param("status") OrderStatus status,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);
}
