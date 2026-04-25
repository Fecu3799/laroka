package com.laroka.backend.order.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.exception.ProductNotFoundException;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.order.domain.OrderStateMachine;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderStatusHistory;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.exception.OrderNotFoundException;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final BranchProductRepository branchProductRepository;

    @Transactional
    public Order createOrder(Order order, List<OrderItem> items, PaymentMethod paymentMethod) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("El pedido debe contener al menos un ítem");
        }

        Branch branch = branchRepository.findById(order.getBranch().getId())
                .orElseThrow(() -> new BranchNotFoundException(order.getBranch().getId()));

        if (order.getOrderType() == OrderType.DELIVERY
                && (order.getDeliveryAddress() == null || order.getDeliveryAddress().isBlank())) {
            throw new BusinessException("La dirección de entrega es obligatoria para pedidos de tipo DELIVERY");
        }

        List<OrderItem> resolvedItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProduct().getId()));

            BigDecimal unitPrice = branchProductRepository
                    .findByBranchIdAndProductId(branch.getId(), product.getId())
                    .map(bp -> bp.getPriceOverride() != null ? bp.getPriceOverride() : product.getPrice())
                    .orElse(product.getPrice());

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(subtotal);

            resolvedItems.add(OrderItem.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build());
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El total del pedido debe ser mayor a cero");
        }

        order.setId(UUID.randomUUID());
        order.setBranch(branch);
        order.setPizzeria(branch.getPizzeria());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(total);

        for (OrderItem item : resolvedItems) {
            item.setOrder(order);
        }
        order.setItems(resolvedItems);

        Order saved = orderRepository.save(order);
        recordHistory(saved, null, OrderStatus.PENDING_PAYMENT);

        if (paymentMethod == PaymentMethod.CASH) {
            saved = doTransition(saved, OrderStatus.RECEIVED);
        }

        return orderRepository.findByIdWithDetails(saved.getId()).orElseThrow();
    }

    @Transactional
    public Order transitionStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return doTransition(order, newStatus);
    }

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return orderRepository.findByIdWithBranch(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> getHistory(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return historyRepository.findByOrderIdOrderByChangedAtAsc(orderId);
    }

    private Order doTransition(Order order, OrderStatus newStatus) {
        List<OrderStatus> validNext = OrderStateMachine.getNextValidStatuses(order);
        if (!validNext.contains(newStatus)) {
            throw new BusinessException(
                    "Transición de estado inválida: " + order.getStatus() + " → " + newStatus
                    + " para pedido de tipo " + order.getOrderType());
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        recordHistory(saved, previous, newStatus);
        return saved;
    }

    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus) {
        historyRepository.save(OrderStatusHistory.builder()
                .id(UUID.randomUUID())
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedAt(LocalDateTime.now())
                .build());
    }
}
