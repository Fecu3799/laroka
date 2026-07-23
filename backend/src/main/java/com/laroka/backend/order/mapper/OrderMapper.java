package com.laroka.backend.order.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.order.dto.BackofficeOrderDetailDTO;
import com.laroka.backend.order.dto.BackofficeOrderItemDTO;
import com.laroka.backend.order.dto.BackofficeOrderResponseDTO;
import com.laroka.backend.order.dto.CreateOrderRequestDTO;
import com.laroka.backend.order.dto.CreateOrderResponseDTO;
import com.laroka.backend.order.dto.OrderDiscountDTO;
import com.laroka.backend.order.dto.OrderItemResponseDTO;
import com.laroka.backend.order.dto.OrderItemStatusDTO;
import com.laroka.backend.order.dto.OrderStatusHistoryDTO;
import com.laroka.backend.order.dto.OrderStatusResponseDTO;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderDiscount;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatusHistory;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.service.AppliedDiscount;
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
                .origin(dto.getOrigin() != null ? dto.getOrigin() : OrderOrigin.CLIENT)
                .pushSubscriptionId(dto.getPushSubscriptionId())
                .build();
    }

    public List<OrderItem> toItems(CreateOrderRequestDTO dto) {
        return dto.getItems().stream()
                .map(item -> OrderItem.builder()
                        .product(Product.builder().id(item.getProductId()).build())
                        // US-HH-02: segunda mitad opcional. El service la resuelve y valida.
                        .secondProduct(item.getSecondProductId() != null
                                ? Product.builder().id(item.getSecondProductId()).build()
                                : null)
                        // US-SIZE-03: tamaño opcional. El service lo resuelve y valida.
                        .productSize(item.getProductSizeId() != null
                                ? ProductSize.builder().id(item.getProductSizeId()).build()
                                : null)
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

    public OrderStatusResponseDTO toStatusResponseDTO(Order order, List<OrderStatusHistory> history,
                                                      PaymentMethod paymentMethod) {
        return OrderStatusResponseDTO.builder()
                .status(order.getStatus())
                .paymentMethod(paymentMethod)
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
        // US-HH-01: secondProduct puede ser null (ítem simple). Debe venir inicializado desde la
        // query (items.secondProduct en el @EntityGraph) para leer su name fuera de sesión.
        Product secondProduct = item.getSecondProduct();
        // Mismo criterio para el tamaño: viene del @EntityGraph (items.productSize), no de un
        // lazy load. Este DTO alimenta el detalle del backoffice, y con él ticket y comanda.
        ProductSize productSize = item.getProductSize();
        return OrderItemResponseDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .secondProductId(secondProduct != null ? secondProduct.getId() : null)
                .secondProductName(secondProduct != null ? secondProduct.getName() : null)
                .productSizeId(productSize != null ? productSize.getId() : null)
                .sizeName(productSize != null ? productSize.getSize().name() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    public List<OrderItemStatusDTO> toItemStatusDTOList(List<OrderItem> items) {
        return items.stream().map(this::toItemStatusDTO).toList();
    }

    private OrderItemStatusDTO toItemStatusDTO(OrderItem item) {
        // US-HH-F-02: secondProduct debe venir inicializado desde la query
        // (LEFT JOIN FETCH en findByOrderIdWithProduct) para leer su name fuera de sesión.
        // Mismo criterio para productSize, que acá viaja como sizeName.
        Product secondProduct = item.getSecondProduct();
        ProductSize productSize = item.getProductSize();
        return OrderItemStatusDTO.builder()
                .name(item.getProduct().getName())
                .secondProductName(secondProduct != null ? secondProduct.getName() : null)
                .sizeName(productSize != null ? productSize.getSize().name() : null)
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
                .orderNumber(order.getOrderNumber())
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
                .refundedAmount(payment != null ? payment.getRefundedAmount() : null)
                .statusHistory(detail.history().stream().map(this::toHistoryDTO).toList())
                .cancellationReason(cancellationReason)
                .discount(toDiscountDTO(detail.discount()))
                .build();
    }

    /** US-19-03: descuento vigente del pedido, o null si no tiene ninguno aplicado. */
    private OrderDiscountDTO toDiscountDTO(AppliedDiscount applied) {
        if (applied == null) {
            return null;
        }
        OrderDiscount discount = applied.discount();
        return OrderDiscountDTO.builder()
                .percentage(discount.getPercentage())
                .originalTotalAmount(discount.getOriginalTotalAmount())
                .discountAmount(discount.getDiscountAmount())
                .finalTotalAmount(discount.getFinalTotalAmount())
                .reason(discount.getReason())
                .note(discount.getNote())
                .appliedByName(applied.appliedByName())
                .appliedAt(discount.getAppliedAt())
                .build();
    }

    public BackofficeOrderResponseDTO toBackofficeResponseDTO(Order order, Payment payment) {
        return toBackofficeResponseDTO(order, payment, null);
    }

    public BackofficeOrderResponseDTO toBackofficeResponseDTO(Order order, Payment payment,
                                                              OrderDiscount discount) {
        return BackofficeOrderResponseDTO.builder()
                // US-19-04: el ticket se imprime desde la fila de la lista, así que el
                // descuento viaja acá. appliedByName queda null a propósito: la lista no
                // resuelve nombres (sería una query extra en un endpoint que se refresca
                // por polling) y el ticket no lo usa. El detalle sí lo trae (US-19-03).
                .discount(discount == null ? null : toDiscountDTO(new AppliedDiscount(discount, null)))
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
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
        // US-HH-01: expone la segunda mitad cuando corresponde (null en ítems simples).
        Product secondProduct = item.getSecondProduct();
        // Igual que secondProduct, productSize debe venir inicializado desde la query
        // (items.productSize en el @EntityGraph) para leerlo fuera de la sesión.
        ProductSize productSize = item.getProductSize();
        return BackofficeOrderItemDTO.builder()
                .productName(item.getProduct().getName())
                .secondProductName(secondProduct != null ? secondProduct.getName() : null)
                .sizeName(productSize != null ? productSize.getSize().name() : null)
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
