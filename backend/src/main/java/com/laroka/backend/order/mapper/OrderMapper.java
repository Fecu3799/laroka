package com.laroka.backend.order.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.order.dto.BackofficeOrderDetailDTO;
import com.laroka.backend.order.dto.BackofficeOrderItemDTO;
import com.laroka.backend.order.dto.BackofficeOrderResponseDTO;
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
import com.laroka.backend.order.service.BackofficeOrderDetail;
import com.laroka.backend.payment.entity.Payment;

@Component
public class OrderMapper {

    public Order toEntity(CreateOrderRequestDTO dto) {
        return Order.builder()
                .branch(Branch.builder().id(dto.getBranchId()).build())
                .orderType(dto.getOrderType())
                .deliveryAddress(dto.getDeliveryAddress())
                .notes(dto.getNotes())
                .customerName(dto.getCustomerName())
                .customerPhone(dto.getCustomerPhone())
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

    public BackofficeOrderDetailDTO toBackofficeDetailDTO(BackofficeOrderDetail detail) {
        Order order = detail.order();
        Payment payment = detail.payment();

        String cancellationReason = detail.history().stream()
                .filter(h -> (h.getToStatus() == com.laroka.backend.order.entity.OrderStatus.CANCELLED
                        || h.getToStatus() == com.laroka.backend.order.entity.OrderStatus.CANCELLATION_REQUESTED)
                        && h.getCancellationReason() != null)
                .map(h -> h.getCancellationReason())
                .findFirst()
                .orElse(null);

        return BackofficeOrderDetailDTO.builder()
                .id(order.getId())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .status(order.getStatus())
                .orderType(order.getOrderType())
                .origin(order.getOrigin())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .totalAmount(order.getTotalAmount())
                .deliveryAddress(order.getDeliveryAddress())
                .notes(order.getNotes())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .branchName(order.getBranch().getName())
                .items(order.getItems().stream().map(this::toItemResponseDTO).toList())
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .paymentMethod(payment != null ? payment.getMethod() : null)
                .paidAt(payment != null ? payment.getPaidAt() : null)
                .statusHistory(detail.history().stream().map(this::toHistoryDTO).toList())
                .cancellationReason(cancellationReason)
                .build();
    }

    public BackofficeOrderResponseDTO toBackofficeResponseDTO(Order order, Payment payment) {
        return BackofficeOrderResponseDTO.builder()
                .id(order.getId())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .paymentMethod(payment != null ? payment.getMethod() : null)
                .orderType(order.getOrderType())
                .origin(order.getOrigin())
                .deliveryAddress(order.getDeliveryAddress())
                .notes(order.getNotes())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .items(order.getItems().stream().map(this::toBackofficeItemDTO).toList())
                .build();
    }

    private BackofficeOrderItemDTO toBackofficeItemDTO(OrderItem item) {
        return BackofficeOrderItemDTO.builder()
                .productName(item.getProduct().getName())
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
                .cancellationReason(h.getCancellationReason())
                .cancelledByStaff(h.getStaffUserId() != null)
                .build();
    }
}
