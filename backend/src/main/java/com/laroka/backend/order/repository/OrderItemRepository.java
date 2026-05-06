package com.laroka.backend.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.order.entity.OrderItem;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);

    @Query("SELECT i FROM OrderItem i JOIN FETCH i.product WHERE i.order.id = :orderId")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") UUID orderId);
}
