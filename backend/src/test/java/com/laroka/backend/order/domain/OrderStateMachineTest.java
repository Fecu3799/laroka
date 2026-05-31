package com.laroka.backend.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;

class OrderStateMachineTest {

    private Order orderWithStatus(OrderStatus status, OrderType type) {
        return Order.builder()
                .status(status)
                .orderType(type)
                .branch(Branch.builder().id(1).build())
                .build();
    }

    // --- canCancel ---

    @Test
    void canCancel_fromReceived_returnsTrue() {
        assertThat(OrderStateMachine.canCancel(OrderStatus.RECEIVED)).isTrue();
    }

    @Test
    void canCancel_fromInPreparation_returnsTrue() {
        assertThat(OrderStateMachine.canCancel(OrderStatus.IN_PREPARATION)).isTrue();
    }

    @Test
    void canCancel_fromOnTheWay_returnsFalse() {
        assertThat(OrderStateMachine.canCancel(OrderStatus.ON_THE_WAY)).isFalse();
    }

    @Test
    void canCancel_fromReadyForPickup_returnsFalse() {
        assertThat(OrderStateMachine.canCancel(OrderStatus.READY_FOR_PICKUP)).isFalse();
    }

    @Test
    void canCancel_fromCancellationRequested_returnsFalse() {
        assertThat(OrderStateMachine.canCancel(OrderStatus.CANCELLATION_REQUESTED)).isFalse();
    }

    @Test
    void canCancel_fromTerminalStates_returnsFalse() {
        assertThat(OrderStateMachine.canCancel(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.canCancel(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void canCancel_fromPendingPayment_returnsTrue() {
        assertThat(OrderStateMachine.canCancel(OrderStatus.PENDING_PAYMENT)).isTrue();
    }

    // --- getNextValidStatuses: flujos de cancelación ---

    @Test
    void getNextValidStatuses_received_includesCancelled() {
        Order order = orderWithStatus(OrderStatus.RECEIVED, OrderType.DELIVERY);
        assertThat(OrderStateMachine.getNextValidStatuses(order))
                .contains(OrderStatus.CANCELLED, OrderStatus.IN_PREPARATION);
    }

    @Test
    void getNextValidStatuses_inPreparationDelivery_includesCancellationRequested() {
        Order order = orderWithStatus(OrderStatus.IN_PREPARATION, OrderType.DELIVERY);
        assertThat(OrderStateMachine.getNextValidStatuses(order))
                .contains(OrderStatus.CANCELLATION_REQUESTED, OrderStatus.ON_THE_WAY);
    }

    @Test
    void getNextValidStatuses_inPreparationTakeaway_includesCancellationRequested() {
        Order order = orderWithStatus(OrderStatus.IN_PREPARATION, OrderType.TAKEAWAY);
        assertThat(OrderStateMachine.getNextValidStatuses(order))
                .contains(OrderStatus.CANCELLATION_REQUESTED, OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void getNextValidStatuses_cancellationRequested_allowsApproveAndReject() {
        Order order = orderWithStatus(OrderStatus.CANCELLATION_REQUESTED, OrderType.DELIVERY);
        assertThat(OrderStateMachine.getNextValidStatuses(order))
                .containsExactlyInAnyOrder(OrderStatus.CANCELLED, OrderStatus.IN_PREPARATION);
    }

    @Test
    void getNextValidStatuses_terminalStates_isEmpty() {
        assertThat(OrderStateMachine.getNextValidStatuses(
                orderWithStatus(OrderStatus.CANCELLED, OrderType.DELIVERY))).isEmpty();
        assertThat(OrderStateMachine.getNextValidStatuses(
                orderWithStatus(OrderStatus.DELIVERED, OrderType.DELIVERY))).isEmpty();
    }
}
