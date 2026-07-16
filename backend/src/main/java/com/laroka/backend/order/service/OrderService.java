package com.laroka.backend.order.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.laroka.backend.notification.repository.PushSubscriptionRepository;
import com.laroka.backend.notification.service.PushNotificationService;
import com.laroka.backend.order.dto.OrderFilterParams;
import com.laroka.backend.order.repository.OrderSpecification;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.exception.ProductNotFoundException;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.order.domain.OrderStateMachine;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderStatusHistory;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.exception.OrderNotFoundException;
import com.laroka.backend.order.exception.ProductUnavailableException;
import com.laroka.backend.order.repository.BranchOrderSequenceRepository;
import com.laroka.backend.order.repository.OrderItemRepository;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.repository.WorkShiftRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final BranchOrderSequenceRepository branchOrderSequenceRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderItemRepository orderItemRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final BranchProductRepository branchProductRepository;
    private final IdempotencyStore idempotencyStore;
    private final PaymentRepository paymentRepository;
    private final WorkShiftRepository workShiftRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushNotificationService pushNotificationService;
    private final PaymentGateway paymentGateway;

    /**
     * Motivo registrado cuando el auto-cierre de turno cancela un pedido activo.
     * Se distingue deliberadamente de una cancelación normal: la responsabilidad
     * es operativa (el local no llegó a procesar el pedido antes de cerrar el
     * turno), no del cliente.
     */
    public static final String SHIFT_AUTO_CLOSE_CANCELLATION_REASON = "No se pudo procesar a tiempo";

    /** Estados en los que un pedido sigue activo (no terminal) dentro de un turno. */
    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(
            OrderStatus.RECEIVED, OrderStatus.IN_PREPARATION,
            OrderStatus.ON_THE_WAY, OrderStatus.READY_FOR_PICKUP);

    /** Estados terminales (no reversibles): un pedido acá ya no está activo. */
    private static final List<OrderStatus> TERMINAL_ORDER_STATUSES = List.of(
            OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    @Transactional
    public OrderCreationResult createOrder(Order order, List<OrderItem> items,
                                           PaymentMethod paymentMethod, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyStore.get(idempotencyKey)
                    .map(cached -> {
                        log.info("Idempotency key hit — returning existing order | orderId={} key={}",
                                cached.getId(), idempotencyKey);
                        return new OrderCreationResult(cached, true);
                    })
                    .orElseGet(() -> doCreateOrder(order, items, paymentMethod, idempotencyKey));
        }
        return doCreateOrder(order, items, paymentMethod, null);
    }

    @Transactional
    public Order transitionStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", orderId);
                    return new OrderNotFoundException(orderId);
                });
        return doTransition(order, newStatus);
    }

    @Transactional
    public Order transitionStatusForBackoffice(UUID orderId, OrderStatus newStatus, String reason,
                                               Integer branchId, Integer staffUserId) {
        Order order = orderRepository.findByIdWithBranch(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", orderId);
                    return new OrderNotFoundException(orderId);
                });

        if (!order.getBranch().getId().equals(branchId)) {
            log.warn("Branch mismatch on status update | orderId={} orderBranch={} userBranch={}",
                    orderId, order.getBranch().getId(), branchId);
            throw new AccessDeniedException("El pedido no pertenece a la sucursal del usuario");
        }

        List<OrderStatus> validNext = OrderStateMachine.getNextValidStatuses(order);
        if (!validNext.contains(newStatus)) {
            throw new BusinessException(
                    "Transición de estado inválida: " + order.getStatus() + " → " + newStatus
                    + " para pedido de tipo " + order.getOrderType());
        }

        if (newStatus == OrderStatus.CANCELLED && (reason == null || reason.isBlank())) {
            throw new BusinessException("El motivo de cancelación es obligatorio");
        }

        if (newStatus == OrderStatus.DELIVERED) {
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            if (payment == null || payment.getStatus() == PaymentStatus.PENDING) {
                throw new BusinessException("El pedido no puede marcarse como entregado con pago pendiente");
            }
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        recordHistory(saved, previous, newStatus, staffUserId,
                newStatus == OrderStatus.CANCELLED ? reason : null);
        log.info("Backoffice status transition | orderId={} from={} to={} staffUserId={}",
                saved.getId(), previous, newStatus, staffUserId);

        // US-17-02: cancelación directa desde RECEIVED → reembolso TOTAL automático si
        // el pago fue MercadoPago aprobado. No bloquea si el gateway falla. Solo RECEIVED:
        // un pedido PENDING_PAYMENT no puede tener un pago MP aprobado (el webhook aprueba
        // el pago y lo mueve a RECEIVED en la misma transacción). La cancelación tardía
        // (IN_PREPARATION → CANCELLATION_REQUESTED → CANCELLED) va por otro camino con
        // reembolso parcial (US-17-03), no por acá.
        if (newStatus == OrderStatus.CANCELLED && previous == OrderStatus.RECEIVED) {
            refundIfMercadoPagoApproved(saved);
        }
        return saved;
    }

    @Transactional
    public Order transitionToPreviousStatusForBackoffice(UUID orderId, Integer branchId, Integer staffUserId) {
        Order order = orderRepository.findByIdWithBranch(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", orderId);
                    return new OrderNotFoundException(orderId);
                });

        if (!order.getBranch().getId().equals(branchId)) {
            log.warn("Branch mismatch on previous status | orderId={} orderBranch={} userBranch={}",
                    orderId, order.getBranch().getId(), branchId);
            throw new AccessDeniedException("El pedido no pertenece a la sucursal del usuario");
        }

        OrderStatus previousStatus = OrderStateMachine.getPreviousStatus(order.getStatus(), order.getOrderType());
        if (previousStatus == null) {
            throw new BusinessException("No se puede retroceder este estado: " + order.getStatus());
        }

        OrderStatus current = order.getStatus();
        order.setStatus(previousStatus);
        Order saved = orderRepository.save(order);
        recordHistory(saved, current, previousStatus, staffUserId);
        log.info("Backoffice previous status transition | orderId={} from={} to={} staffUserId={}",
                saved.getId(), current, previousStatus, staffUserId);
        return saved;
    }

    @Transactional
    public void cancelOrder(UUID orderId, String reason) {
        Order order = orderRepository.findByIdWithBranch(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", orderId);
                    return new OrderNotFoundException(orderId);
                });

        if (!OrderStateMachine.canCancel(order.getStatus())) {
            throw new BusinessException("El pedido no puede cancelarse en este estado");
        }

        OrderStatus previous = order.getStatus();
        OrderStatus next = (previous == OrderStatus.RECEIVED || previous == OrderStatus.PENDING_PAYMENT)
                ? OrderStatus.CANCELLED
                : OrderStatus.CANCELLATION_REQUESTED;

        if (next == OrderStatus.CANCELLATION_REQUESTED && (reason == null || reason.isBlank())) {
            throw new BusinessException("El motivo de cancelación es obligatorio");
        }

        order.setStatus(next);
        Order saved = orderRepository.save(order);
        recordHistory(saved, previous, next, null, reason);
        log.info("Order cancel flow | orderId={} from={} to={}", saved.getId(), previous, next);

        // US-17-02: cancelación directa desde RECEIVED → reembolso TOTAL automático si
        // el pago fue MercadoPago aprobado. No bloquea si el gateway falla. Solo RECEIVED:
        // un pedido PENDING_PAYMENT no puede tener un pago MP aprobado (el webhook aprueba
        // el pago y mueve el pedido a RECEIVED en la misma transacción), así que ahí no
        // hay cobro que revertir.
        if (previous == OrderStatus.RECEIVED) {
            refundIfMercadoPagoApproved(saved);
        }

        // La emisión del evento SSE se hace en OrderController, después de que esta
        // transacción commitea (mismo patrón que BackofficeOrderController). Hacer el
        // emitter.send() dentro de la transacción retenía la conexión JDBC mientras se
        // escribía a un socket potencialmente lento/muerto, agotando el pool de Hikari;
        // además evita notificar al cliente un cambio que luego haga rollback.
    }

    /**
     * Cancela todos los pedidos activos (RECEIVED, IN_PREPARATION, ON_THE_WAY,
     * READY_FOR_PICKUP) de un turno durante su cierre automático.
     *
     * A diferencia del cierre manual —que bloquea cuando hay pedidos activos sin
     * resolver— el auto-cierre los resuelve cancelándolos: así ningún pedido queda
     * huérfano referenciando un turno CLOSED ni atrasado e invisible al abrir el
     * turno siguiente. Cada cancelación registra el motivo operativo
     * {@link #SHIFT_AUTO_CLOSE_CANCELLATION_REASON} (distinto de una cancelación
     * normal, visible en el historial) y notifica al cliente por el mismo canal
     * que cualquier otra cancelación (vía {@code recordHistory}).
     *
     * Si el pedido tenía un pago MercadoPago aprobado, se dispara un reembolso
     * TOTAL: la responsabilidad es del local, no del cliente, por lo que no aplica
     * el reembolso parcial de cancelaciones tardías iniciadas por el cliente
     * (Sprint 17). Ver docs/KNOWN_ISSUES.md.
     */
    @Transactional
    public void cancelActiveOrdersForShiftAutoClose(UUID shiftId) {
        List<Order> activeOrders = orderRepository.findByShiftIdAndStatusIn(shiftId, ACTIVE_ORDER_STATUSES);
        for (Order order : activeOrders) {
            OrderStatus previous = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            Order saved = orderRepository.save(order);
            recordHistory(saved, previous, OrderStatus.CANCELLED, null, SHIFT_AUTO_CLOSE_CANCELLATION_REASON);
            refundIfMercadoPagoApproved(saved);
            log.info("Order cancelled by shift auto-close | orderId={} from={} shiftId={}",
                    saved.getId(), previous, shiftId);
        }
    }

    /**
     * Reembolso TOTAL del pago del pedido si —y solo si— existe un {@link Payment}
     * APPROVED de método MERCADOPAGO. Invoca {@code refundPayment(paymentId, null)}
     * (monto null → reembolso completo). Un pago en efectivo no dispara reembolso: no
     * hubo cobro electrónico que revertir; un pago no aprobado (PENDING) tampoco.
     *
     * El fallo del gateway se loguea para acción manual y NO aborta la cancelación
     * del pedido (best-effort, no bloqueante): el cliente/operador no debe quedar
     * bloqueado por un fallo del gateway. La persistencia explícita del estado de
     * fallo queda pendiente de US-17-04 (modelo de tracking de reembolsos); hoy el
     * pago queda en APPROVED y el reembolso pendiente de reintento manual.
     *
     * Usado tanto por el auto-cierre de turno como por la cancelación directa de
     * pedidos previos a IN_PREPARATION (US-17-02).
     */
    private void refundIfMercadoPagoApproved(Order order) {
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment == null
                || payment.getStatus() != PaymentStatus.APPROVED
                || payment.getMethod() != PaymentMethod.MERCADOPAGO) {
            return;
        }
        try {
            paymentGateway.refundPayment(payment.getMercadopagoPaymentId(), null);
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Full refund issued | orderId={} mpPaymentId={}",
                    order.getId(), payment.getMercadopagoPaymentId());
        } catch (Exception e) {
            log.error("Full refund FAILED — manual action required | orderId={} mpPaymentId={} error={}",
                    order.getId(), payment.getMercadopagoPaymentId(), e.getMessage());
        }
    }

    @Transactional
    public void resolveCancellationRequest(UUID orderId, String action, Integer branchId, Integer staffUserId) {
        Order order = orderRepository.findByIdWithBranch(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", orderId);
                    return new OrderNotFoundException(orderId);
                });

        if (!order.getBranch().getId().equals(branchId)) {
            log.warn("Branch mismatch on cancel-request | orderId={} orderBranch={} userBranch={}",
                    orderId, order.getBranch().getId(), branchId);
            throw new AccessDeniedException("El pedido no pertenece a la sucursal del usuario");
        }

        if (order.getStatus() != OrderStatus.CANCELLATION_REQUESTED) {
            throw new BusinessException("El pedido no está en estado de cancelación solicitada");
        }

        OrderStatus next = switch (action) {
            case "APPROVE" -> OrderStatus.CANCELLED;
            case "REJECT" -> OrderStatus.IN_PREPARATION;
            default -> throw new BusinessException("action debe ser APPROVE o REJECT");
        };

        OrderStatus previous = order.getStatus();
        order.setStatus(next);
        Order saved = orderRepository.save(order);
        recordHistory(saved, previous, next, staffUserId);
        log.info("Cancellation request resolved | orderId={} action={} to={} staffUserId={}",
                saved.getId(), action, next, staffUserId);
    }

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return orderRepository.findByIdWithBranch(id)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", id);
                    return new OrderNotFoundException(id);
                });
    }

    @Transactional(readOnly = true)
    public Order findByIdWithHistory(UUID id) {
        Order order = orderRepository.findByIdWithBranchAndHistory(id)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", id);
                    return new OrderNotFoundException(id);
                });
        log.debug("Order status poll | orderId={} currentStatus={}", id, order.getStatus());
        return order;
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> getHistory(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            log.warn("Order not found | orderId={}", orderId);
            throw new OrderNotFoundException(orderId);
        }
        return historyRepository.findByOrderIdOrderByChangedAtAsc(orderId);
    }

    @Transactional(readOnly = true)
    public BackofficeOrderDetail getOrderDetailForBackoffice(UUID orderId, Integer branchId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", orderId);
                    return new OrderNotFoundException(orderId);
                });

        if (!order.getBranch().getId().equals(branchId)) {
            log.warn("Branch mismatch on order detail | orderId={} orderBranch={} userBranch={}",
                    orderId, order.getBranch().getId(), branchId);
            throw new AccessDeniedException("El pedido no pertenece a la sucursal del usuario");
        }

        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);

        return new BackofficeOrderDetail(order, payment, history);
    }

    @Transactional(readOnly = true)
    public Page<BackofficeOrderRow> findOrderHistoryByBranch(Integer branchId, OrderStatus statusFilter,
                                                              LocalDateTime dateFrom, LocalDateTime dateTo,
                                                              int page, int size) {
        Specification<Order> spec = Specification
                .where(OrderSpecification.branchIs(branchId))
                .and(OrderSpecification.statusIn(TERMINAL_ORDER_STATUSES));

        if (statusFilter != null && TERMINAL_ORDER_STATUSES.contains(statusFilter)) {
            spec = spec.and(OrderSpecification.statusIs(statusFilter));
        }
        if (dateFrom != null) {
            spec = spec.and(OrderSpecification.createdAtFrom(dateFrom));
        }
        if (dateTo != null) {
            spec = spec.and(OrderSpecification.createdAtTo(dateTo));
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findAll(spec, pageRequest);

        if (orderPage.isEmpty()) {
            return Page.empty(pageRequest);
        }

        List<UUID> ids = orderPage.getContent().stream().map(Order::getId).toList();

        Map<UUID, Order> withItems = orderRepository.findByIdsWithItems(ids)
                .stream().collect(Collectors.toMap(Order::getId, o -> o));

        Map<UUID, Payment> paymentByOrderId = paymentRepository.findByOrderIdIn(ids)
                .stream().collect(Collectors.toMap(p -> p.getOrder().getId(), p -> p, (a, b) -> a));

        List<BackofficeOrderRow> rows = orderPage.getContent().stream()
                .map(o -> new BackofficeOrderRow(
                        withItems.getOrDefault(o.getId(), o),
                        paymentByOrderId.get(o.getId())))
                .toList();

        return new PageImpl<>(rows, pageRequest, orderPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<BackofficeOrderRow> findActiveOrdersByBranch(Integer branchId, OrderFilterParams params) {
        return findActiveOrdersByBranch(branchId, null, params);
    }

    public List<BackofficeOrderRow> findActiveOrdersByBranch(Integer branchId, UUID shiftId, OrderFilterParams params) {
        // Con shiftId, la lista del turno (todos sus pedidos) reemplaza al filtrado
        // por fecha. Sin shiftId, comportamiento original: pedidos no terminados de
        // la sucursal, acotados opcionalmente por dateFrom/dateTo.
        boolean byShift = shiftId != null;
        List<Order> orders = byShift
                ? orderRepository.findByBranchIdAndShiftId(branchId, shiftId)
                : orderRepository.findActiveByBranchId(branchId, TERMINAL_ORDER_STATUSES);

        if (orders.isEmpty()) {
            return List.of();
        }

        Comparator<Order> comparator = params.ascending()
                ? Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        List<Order> filtered = orders.stream()
                .filter(o -> params.status() == null || o.getStatus() == params.status())
                .filter(o -> byShift || params.dateFrom() == null || !o.getCreatedAt().isBefore(params.dateFrom()))
                .filter(o -> byShift || params.dateTo() == null || !o.getCreatedAt().isAfter(params.dateTo()))
                .sorted(comparator)
                .toList();

        if (filtered.isEmpty()) {
            return List.of();
        }

        List<UUID> orderIds = filtered.stream().map(Order::getId).toList();
        Map<UUID, Payment> paymentByOrderId = paymentRepository.findByOrderIdIn(orderIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getOrder().getId(), p -> p, (a, b) -> a));

        return filtered.stream()
                .map(o -> new BackofficeOrderRow(o, paymentByOrderId.get(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BackofficeOrderRow findOrderRowById(UUID orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found | orderId={}", orderId);
                    return new OrderNotFoundException(orderId);
                });
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return new BackofficeOrderRow(order, payment);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> findItemsByOrderId(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            log.warn("Order not found | orderId={}", orderId);
            throw new OrderNotFoundException(orderId);
        }
        return orderItemRepository.findByOrderIdWithProduct(orderId);
    }

    private OrderCreationResult doCreateOrder(Order order, List<OrderItem> items,
                                              PaymentMethod paymentMethod, String idempotencyKey) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("El pedido debe contener al menos un ítem");
        }

        Branch branch = branchRepository.findById(order.getBranch().getId())
                .orElseThrow(() -> {
                    log.warn("Branch not found | branchId={}", order.getBranch().getId());
                    return new BranchNotFoundException(order.getBranch().getId());
                });

        if (!branch.isAcceptingOrders()) {
            log.warn("Order rejected — branch not accepting orders | branchId={}", branch.getId());
            throw new BusinessException("El local no está aceptando pedidos en este momento");
        }

        if (order.getOrderType() == OrderType.DELIVERY
                && (order.getDeliveryAddress() == null || order.getDeliveryAddress().isBlank())) {
            throw new BusinessException("La dirección de entrega es obligatoria para pedidos de tipo DELIVERY");
        }

        List<OrderItem> resolvedItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProduct().getId()));

            Optional<BranchProduct> branchProduct =
                    branchProductRepository.findByBranchIdAndProductId(branch.getId(), product.getId());

            // US-15-09: pedidos del client rechazan productos no disponibles en la
            // sucursal (BranchProduct inexistente o available=false). El backoffice
            // (ADMIN/STAFF) puede forzar el pedido igual — ya fue advertido en el
            // frontend (US-15-F-09). Rechaza el pedido completo antes de persistir nada.
            if (order.getOrigin() == OrderOrigin.CLIENT
                    && branchProduct.map(bp -> !Boolean.TRUE.equals(bp.getAvailable())).orElse(true)) {
                log.warn("Order rejected — product not available | branchId={} productId={} productName={}",
                        branch.getId(), product.getId(), product.getName());
                throw new ProductUnavailableException(product.getId(), product.getName());
            }

            BigDecimal unitPrice = branchProduct
                    .map(bp -> bp.getPriceOverride() != null ? bp.getPriceOverride() : product.getPrice())
                    .orElse(product.getPrice());

            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineSubtotal);

            resolvedItems.add(OrderItem.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(lineSubtotal)
                    .build());
        }

        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El total del pedido debe ser mayor a cero");
        }

        BigDecimal deliveryFee = order.getOrderType() == OrderType.DELIVERY
                ? branch.getDeliveryFee()
                : BigDecimal.ZERO;
        BigDecimal serviceFee = branch.getServiceFee();
        BigDecimal totalAmount = subtotal.add(deliveryFee).add(serviceFee);

        order.setId(UUID.randomUUID());
        order.setBranch(branch);
        order.setTenant(branch.getTenant());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setServiceFee(serviceFee);
        order.setTotalAmount(totalAmount);

        for (OrderItem item : resolvedItems) {
            item.setOrder(order);
        }
        order.setItems(resolvedItems);

        WorkShift shift = workShiftRepository.findByBranchIdAndStatus(branch.getId(), ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessException("No hay turno activo para esta sucursal"));
        order.setShift(shift);

        // US-09-02: vínculo opcional con la suscripción Web Push del dispositivo.
        // Si el id no existe en push_subscription, se descarta silenciosamente y
        // el pedido se crea sin vínculo (sin error ni advertencia al cliente).
        if (order.getPushSubscriptionId() != null
                && !pushSubscriptionRepository.existsById(order.getPushSubscriptionId())) {
            log.debug("Push subscription not found, order created without link | pushSubscriptionId={}",
                    order.getPushSubscriptionId());
            order.setPushSubscriptionId(null);
        }

        // Número de orden secuencial por sucursal (US-16B-03). Se asigna lo más
        // tarde posible dentro de la transacción para acotar la ventana del lock de
        // fila del contador; queda serializado con los demás pedidos de la sucursal.
        order.setOrderNumber(branchOrderSequenceRepository.nextOrderNumber(branch.getId()));

        Order saved = orderRepository.save(order);
        recordHistory(saved, null, OrderStatus.PENDING_PAYMENT);

        log.info("Order created | orderId={} branchId={} orderType={} paymentMethod={} subtotal={} deliveryFee={} serviceFee={} total={} origin={} requestId={}",
                saved.getId(), saved.getBranch().getId(), saved.getOrderType(),
                paymentMethod, saved.getSubtotal(), saved.getDeliveryFee(),
                saved.getServiceFee(), saved.getTotalAmount(), saved.getOrigin(), idempotencyKey);

        if (paymentMethod == PaymentMethod.CASH) {
            log.info("Cash order auto-received | orderId={}", saved.getId());
            saved = doTransition(saved, OrderStatus.RECEIVED);
            paymentRepository.save(Payment.builder()
                    .id(UUID.randomUUID())
                    .order(saved)
                    .status(PaymentStatus.PENDING)
                    .method(PaymentMethod.CASH)
                    .build());
        }

        Order result = orderRepository.findByIdWithDetails(saved.getId()).orElseThrow();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.put(idempotencyKey, result);
        }

        // El evento SSE NEW_ORDER se emite en OrderController, después del commit de
        // esta transacción (mismo patrón que BackofficeOrderController). Mantenerlo aquí
        // hacía el emitter.send() bloqueante dentro de la transacción, reteniendo la
        // conexión JDBC; y emitía el evento aun cuando la transacción terminara en rollback.
        return new OrderCreationResult(result, false);
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
        log.info("Order status transition | orderId={} from={} to={}", saved.getId(), previous, newStatus);
        return saved;
    }

    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus) {
        recordHistory(order, fromStatus, toStatus, null, null);
    }

    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, Integer staffUserId) {
        recordHistory(order, fromStatus, toStatus, staffUserId, null);
    }

    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus,
                                Integer staffUserId, String cancellationReason) {
        historyRepository.save(OrderStatusHistory.builder()
                .id(UUID.randomUUID())
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedAt(LocalDateTime.now())
                .staffUserId(staffUserId)
                .cancellationReason(cancellationReason)
                .build());

        // Notificación push al cliente (US-09-03). Fire-and-forget: corre en otro
        // hilo y nunca propaga errores. Los estados sin mensaje definido no
        // disparan envío (lo resuelve el propio servicio).
        pushNotificationService.sendNotification(order, toStatus);
    }
}
