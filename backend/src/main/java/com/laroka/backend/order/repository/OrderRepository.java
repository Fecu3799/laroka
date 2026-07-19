package com.laroka.backend.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime threshold);

    @EntityGraph(attributePaths = {"items", "items.product", "items.secondProduct"})
    @Query("SELECT o FROM Order o WHERE o.branch.id = :branchId AND o.status NOT IN :excluded")
    List<Order> findActiveByBranchId(@Param("branchId") Integer branchId,
                                     @Param("excluded") Collection<OrderStatus> excluded);

    @EntityGraph(attributePaths = {"items", "items.product", "items.secondProduct"})
    List<Order> findByBranchIdAndShiftId(Integer branchId, UUID shiftId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.secondProduct"})
    @Query("SELECT o FROM Order o WHERE o.id IN :ids")
    List<Order> findByIdsWithItems(@Param("ids") Collection<UUID> ids);

    List<Order> findByShiftIdAndStatusIn(UUID shiftId, Collection<OrderStatus> statuses);

    boolean existsByShiftIdAndStatusIn(UUID shiftId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"branch"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithBranch(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"branch", "items", "items.product", "items.secondProduct"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"branch", "statusHistory"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithBranchAndHistory(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE orders SET push_subscription_id = NULL WHERE push_subscription_id = :pushSubscriptionId",
           nativeQuery = true)
    int clearPushSubscription(@Param("pushSubscriptionId") UUID pushSubscriptionId);
}
