package com.laroka.backend.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime threshold);

    @EntityGraph(attributePaths = {"branch"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithBranch(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"branch", "items", "items.product"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);
}
