package com.laroka.backend.order.domain;

import java.util.List;

import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;

public class OrderStateMachine {

    private OrderStateMachine() {}

    public static List<OrderStatus> getNextValidStatuses(Order order) {
        OrderStatus current = order.getStatus();
        OrderType type = order.getOrderType();

        return switch (current) {
            case PENDING_PAYMENT -> List.of(OrderStatus.RECEIVED);
            case RECEIVED -> List.of(OrderStatus.IN_PREPARATION, OrderStatus.CANCELLED);
            case IN_PREPARATION -> type == OrderType.DELIVERY
                    ? List.of(OrderStatus.ON_THE_WAY, OrderStatus.CANCELLATION_REQUESTED)
                    : List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLATION_REQUESTED);
            case ON_THE_WAY -> List.of(OrderStatus.DELIVERED);
            case READY_FOR_PICKUP -> List.of(OrderStatus.DELIVERED);
            case CANCELLATION_REQUESTED -> List.of(OrderStatus.CANCELLED, OrderStatus.IN_PREPARATION);
            case DELIVERED, CANCELLED -> List.of();
        };
    }

    public static boolean canCancel(OrderStatus status) {
        return status == OrderStatus.RECEIVED || status == OrderStatus.IN_PREPARATION;
    }

    public static OrderStatus getPreviousStatus(OrderStatus current, OrderType orderType) {
        return switch (current) {
            case IN_PREPARATION -> OrderStatus.RECEIVED;
            case ON_THE_WAY -> OrderStatus.IN_PREPARATION;
            case READY_FOR_PICKUP -> OrderStatus.IN_PREPARATION;
            default -> null;
        };
    }
}
