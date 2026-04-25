package com.laroka.backend.order.service;

import java.math.BigDecimal;
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
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final BranchProductRepository branchProductRepository;

    @Transactional
    public Order createOrder(Order order, List<OrderItem> items) {
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

        return orderRepository.save(order);
    }
}
