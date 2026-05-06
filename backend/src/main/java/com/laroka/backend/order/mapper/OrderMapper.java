package com.laroka.backend.order.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.order.dto.CreateOrderRequestDTO;
import com.laroka.backend.order.dto.CreateOrderResponseDTO;
import com.laroka.backend.order.dto.OrderItemResponseDTO;
import com.laroka.backend.order.dto.OrderItemStatusDTO;
import com.laroka.backend.order.dto.OrderStatusHistoryDTO;
import com.laroka.backend.order.dto.OrderStatusResponseDTO;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatusHistory;

@Component
public class OrderMapper {

    public Order toEntity(CreateOrderRequestDTO dto) {
        return Order.builder()
                .branch(Branch.builder().id(dto.getBranchId()).build())
                .orderType(dto.getOrderType())
                .deliveryAddress(dto.getDeliveryAddress())
                .notes(dto.getNotes())
                .origin(OrderOrigin.CLIENT)
                .build();
    }

    public List<OrderItem> toItems(CreateOrderRequestDTO dto) {
        return dto.getItems().stream()
                .map(item -> OrderItem.builder()
                        .product(Product.builder().id(item.getProductId()).build())
                        .quantity(item.getQuantity())
                        .build())
                .toList();
    }

    public CreateOrderResponseDTO toResponseDTO(Order order) {
        return CreateOrderResponseDTO.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .totalAmount(order.getTotalAmount())
                .orderType(order.getOrderType())
                .branchName(order.getBranch().getName())
                .items(order.getItems().stream().map(this::toItemResponseDTO).toList())
                .build();
    }

    public OrderStatusResponseDTO toStatusResponseDTO(Order order, List<OrderStatusHistory> history) {
        return OrderStatusResponseDTO.builder()
                .status(order.getStatus())
                .orderType(order.getOrderType())
                .branchName(order.getBranch().getName())
                .deliveryAddress(order.getDeliveryAddress())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .totalAmount(order.getTotalAmount())
                .history(history.stream().map(this::toHistoryDTO).toList())
                .build();
    }

    private OrderItemResponseDTO toItemResponseDTO(OrderItem item) {
        return OrderItemResponseDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    public List<OrderItemStatusDTO> toItemStatusDTOList(List<OrderItem> items) {
        return items.stream().map(this::toItemStatusDTO).toList();
    }

    private OrderItemStatusDTO toItemStatusDTO(OrderItem item) {
        return OrderItemStatusDTO.builder()
                .name(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    private OrderStatusHistoryDTO toHistoryDTO(OrderStatusHistory h) {
        return OrderStatusHistoryDTO.builder()
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .changedAt(h.getChangedAt())
                .build();
    }
}
