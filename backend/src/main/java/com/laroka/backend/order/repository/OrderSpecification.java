package com.laroka.backend.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.data.jpa.domain.Specification;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;

public class OrderSpecification {

    private OrderSpecification() {}

    public static Specification<Order> branchIs(Integer branchId) {
        return (root, q, cb) -> cb.equal(root.get("branch").get("id"), branchId);
    }

    public static Specification<Order> statusIn(Collection<OrderStatus> statuses) {
        return (root, q, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Order> statusIs(OrderStatus status) {
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdAtFrom(LocalDateTime from) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdAtTo(LocalDateTime to) {
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
