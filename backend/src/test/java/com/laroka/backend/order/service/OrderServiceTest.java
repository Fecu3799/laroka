package com.laroka.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.security.access.AccessDeniedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.branch.service.BranchService;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.exception.OrderNotFoundException;
import com.laroka.backend.order.mapper.OrderMapper;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.repository.PaymentRepository;
import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.repository.WorkShiftRepository;
import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.shared.exception.BusinessException;
import com.laroka.backend.order.dto.OrderFilterParams;
import com.laroka.backend.order.entity.OrderStatusHistory;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BranchService branchService;
    @Mock private ProductRepository productRepository;
    @Mock private BranchProductRepository branchProductRepository;
    @Mock private IdempotencyStore idempotencyStore;
    @Mock private PaymentRepository paymentRepository;
    @Mock private com.laroka.backend.notification.service.NotificationService notificationService;
    @Mock private OrderMapper orderMapper;
    @Mock private WorkShiftRepository workShiftRepository;
    @Mock private com.laroka.backend.notification.repository.PushSubscriptionRepository pushSubscriptionRepository;
    @Mock private com.laroka.backend.notification.service.PushNotificationService pushNotificationService;

    @InjectMocks
    private OrderService service;

    // --- helpers ---

    private Tenant tenant() {
        return Tenant.builder().id(1).name("LaRoka").build();
    }

    private Branch branch(Tenant tenant) {
        return Branch.builder()
                .id(1).name("Playa Unión").address("Av. Roca 123").tenant(tenant)
                .deliveryFee(new BigDecimal("500.00"))
                .serviceFee(new BigDecimal("200.00"))
                .build();
    }

    private Product product(BigDecimal price) {
        return Product.builder().id(1).name("Muzzarella").price(price).build();
    }

    private Order deliveryOrder(Branch branch) {
        return Order.builder()
                .branch(Branch.builder().id(branch.getId()).build())
                .orderType(OrderType.DELIVERY)
                .deliveryAddress("Av. Principal 123")
                .origin(OrderOrigin.CLIENT)
                .build();
    }

    private Order takeawayOrder(Branch branch) {
        return Order.builder()
                .branch(Branch.builder().id(branch.getId()).build())
                .orderType(OrderType.TAKEAWAY)
                .origin(OrderOrigin.CLIENT)
                .build();
    }

    private Order takeawayOrderWithPush(Branch branch, UUID pushSubscriptionId) {
        return Order.builder()
                .branch(Branch.builder().id(branch.getId()).build())
                .orderType(OrderType.TAKEAWAY)
                .origin(OrderOrigin.CLIENT)
                .pushSubscriptionId(pushSubscriptionId)
                .build();
    }

    private OrderItem itemFor(int productId, int quantity) {
        return OrderItem.builder()
                .product(Product.builder().id(productId).build())
                .quantity(quantity)
                .build();
    }

    private Order minimalSavedOrder(OrderStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .status(status)
                .totalAmount(BigDecimal.TEN)
                .orderType(OrderType.TAKEAWAY)
                .branch(branch(tenant()))
                .items(List.of())
                .build();
    }

    private void stubBaseCreation(Branch branch, Product product) {
        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(branchService.isOpen(branch.getId())).thenReturn(true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(branchProductRepository.findByBranchIdAndProductId(branch.getId(), product.getId()))
                .thenReturn(Optional.empty());
        when(workShiftRepository.findByBranchIdAndStatus(branch.getId(), ShiftStatus.OPEN))
                .thenReturn(Optional.of(WorkShift.builder().id(UUID.randomUUID()).build()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findByIdWithDetails(any())).thenReturn(Optional.of(minimalSavedOrder(OrderStatus.PENDING_PAYMENT)));
    }

    // --- createOrder: cálculo de total ---

    @Test
    void createOrder_withBasePrice_usesProductPriceInTotal() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 2)), PaymentMethod.MERCADOPAGO, "key-1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getSubtotal()).isEqualByComparingTo("5600.00");
        assertThat(captor.getValue().getDeliveryFee()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("5800.00");
    }

    @Test
    void createOrder_withPriceOverride_usesBranchOverrideInTotal() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));

        BranchProduct bp = BranchProduct.builder()
                .branch(branch).product(product)
                .available(true).priceOverride(new BigDecimal("3100.00"))
                .build();

        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(branchService.isOpen(1)).thenReturn(true);
        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(branchProductRepository.findByBranchIdAndProductId(1, 1)).thenReturn(Optional.of(bp));
        when(workShiftRepository.findByBranchIdAndStatus(branch.getId(), ShiftStatus.OPEN))
                .thenReturn(Optional.of(WorkShift.builder().id(UUID.randomUUID()).build()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findByIdWithDetails(any())).thenReturn(Optional.of(minimalSavedOrder(OrderStatus.PENDING_PAYMENT)));

        service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-2");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getSubtotal()).isEqualByComparingTo("3100.00");
        assertThat(captor.getValue().getDeliveryFee()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("3300.00");
    }

    @Test
    void createOrder_delivery_appliesDeliveryFee() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        service.createOrder(deliveryOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-d1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getSubtotal()).isEqualByComparingTo("2800.00");
        assertThat(saved.getDeliveryFee()).isEqualByComparingTo("500.00");
        assertThat(saved.getServiceFee()).isEqualByComparingTo("200.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("3500.00");
    }

    @Test
    void createOrder_takeaway_hasZeroDeliveryFee() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-t1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryFee()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("3000.00");
    }

    // --- createOrder: asignación de turno (shiftId) ---

    @Test
    void createOrder_withActiveShift_assignsShiftToOrder() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        UUID shiftId = UUID.randomUUID();
        WorkShift activeShift = WorkShift.builder().id(shiftId).build();
        when(workShiftRepository.findByBranchIdAndStatus(branch.getId(), ShiftStatus.OPEN))
                .thenReturn(Optional.of(activeShift));

        service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-shift-1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getShift()).isNotNull();
        assertThat(captor.getValue().getShift().getId()).isEqualTo(shiftId);
    }

    @Test
    void createOrder_noActiveShift_throwsBusinessException() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));

        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(branchService.isOpen(branch.getId())).thenReturn(true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(branchProductRepository.findByBranchIdAndProductId(branch.getId(), product.getId()))
                .thenReturn(Optional.empty());
        when(workShiftRepository.findByBranchIdAndStatus(branch.getId(), ShiftStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-shift-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("turno activo");
        verify(orderRepository, never()).save(any());
    }

    // --- createOrder: vínculo con suscripción push (US-09-02) ---

    @Test
    void createOrder_withValidPushSubscriptionId_linksItToOrder() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        UUID pushId = UUID.randomUUID();
        when(pushSubscriptionRepository.existsById(pushId)).thenReturn(true);

        service.createOrder(takeawayOrderWithPush(branch, pushId), List.of(itemFor(1, 1)),
                PaymentMethod.MERCADOPAGO, "key-push-1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getPushSubscriptionId()).isEqualTo(pushId);
    }

    @Test
    void createOrder_withNonExistentPushSubscriptionId_createsOrderWithoutLinkAndNoError() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        UUID pushId = UUID.randomUUID();
        when(pushSubscriptionRepository.existsById(pushId)).thenReturn(false);

        OrderCreationResult result = service.createOrder(takeawayOrderWithPush(branch, pushId),
                List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-push-2");

        assertThat(result.order()).isNotNull();
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getPushSubscriptionId()).isNull();
    }

    @Test
    void createOrder_withoutPushSubscriptionId_createsOrderWithoutLink() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)),
                PaymentMethod.MERCADOPAGO, "key-push-3");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getPushSubscriptionId()).isNull();
        verify(pushSubscriptionRepository, never()).existsById(any());
    }

    // --- createOrder: validaciones ---

    @Test
    void createOrder_emptyItems_throwsBusinessException() {
        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        Branch branch = branch(tenant());

        assertThatThrownBy(() ->
                service.createOrder(takeawayOrder(branch), List.of(), PaymentMethod.MERCADOPAGO, "key-3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ítem");
    }

    @Test
    void createOrder_branchNotFound_throwsBranchNotFoundException() {
        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        when(branchRepository.findById(1)).thenReturn(Optional.empty());
        Branch branch = branch(tenant());

        assertThatThrownBy(() ->
                service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-4"))
                .isInstanceOf(BranchNotFoundException.class);
    }

    // --- createOrder: validación de horario (US-06-05) ---

    @Test
    void createOrder_branchOpen_createsOrderSuccessfully() {
        Tenant p = tenant();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("1000.00"));
        stubBaseCreation(branch, product);

        OrderCreationResult result = service.createOrder(
                takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-open");

        assertThat(result.order()).isNotNull();
        assertThat(result.fromCache()).isFalse();
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_branchClosed_throwsBusinessException() {
        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        Branch branch = branch(tenant());
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(branchService.isOpen(branch.getId())).thenReturn(false);

        assertThatThrownBy(() ->
                service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-closed"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El local no está disponible en este momento");
    }

    // --- createOrder: idempotencia ---

    @Test
    void createOrder_duplicateIdempotencyKey_returnsCachedOrderWithoutCreatingNew() {
        Order cached = minimalSavedOrder(OrderStatus.RECEIVED);
        when(idempotencyStore.get("dup-key")).thenReturn(Optional.of(cached));

        Branch branch = branch(tenant());
        OrderCreationResult result = service.createOrder(
                takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.CASH, "dup-key");

        assertThat(result.fromCache()).isTrue();
        assertThat(result.order()).isSameAs(cached);
        verify(orderRepository, org.mockito.Mockito.never()).save(any());
    }

    // --- transitionStatus: flujos válidos ---

    @Test
    void transitionStatus_validDeliveryStep_updatesStatus() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.transitionStatus(orderId, OrderStatus.IN_PREPARATION);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(orderRepository).save(order);
        verify(historyRepository).save(any());
    }

    @Test
    void transitionStatus_validTakeawayStep_updatesStatus() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.TAKEAWAY)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.transitionStatus(orderId, OrderStatus.READY_FOR_PICKUP);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    // --- transitionStatus: transiciones inválidas cruzadas ---

    @Test
    void transitionStatus_onTheWayOnTakeaway_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.TAKEAWAY)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.transitionStatus(orderId, OrderStatus.ON_THE_WAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ON_THE_WAY")
                .hasMessageContaining("TAKEAWAY");
    }

    @Test
    void transitionStatus_readyForPickupOnDelivery_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.DELIVERY)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.transitionStatus(orderId, OrderStatus.READY_FOR_PICKUP))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("READY_FOR_PICKUP")
                .hasMessageContaining("DELIVERY");
    }

    // --- transitionStatus: pedido no encontrado ---

    @Test
    void transitionStatus_orderNotFound_throwsOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transitionStatus(orderId, OrderStatus.IN_PREPARATION))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // --- findActiveOrdersByBranch ---

    @Test
    void findActiveOrdersByBranch_returnsOnlyActiveOrdersForBranch() {
        int branchId = 1;
        Order received = minimalSavedOrder(OrderStatus.RECEIVED);
        Order inPreparation = minimalSavedOrder(OrderStatus.IN_PREPARATION);

        when(orderRepository.findActiveByBranchId(any(Integer.class), any(Collection.class)))
                .thenReturn(List.of(received, inPreparation));
        when(paymentRepository.findByOrderIdIn(any(Collection.class))).thenReturn(List.of());

        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(branchId, OrderFilterParams.defaults());

        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(row ->
                row.order().getStatus() == OrderStatus.DELIVERED ||
                row.order().getStatus() == OrderStatus.CANCELLED);
    }

    @Test
    void findActiveOrdersByBranch_noOrders_returnsEmptyList() {
        when(orderRepository.findActiveByBranchId(any(Integer.class), any(Collection.class)))
                .thenReturn(List.of());

        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(99, OrderFilterParams.defaults());

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveOrdersByBranch_withShiftId_filtersByShift() {
        int branchId = 1;
        UUID shiftId = UUID.randomUUID();
        // El path por turno trae todos los pedidos del turno (incluye terminales),
        // a diferencia del path por fecha que parte de los no terminados.
        Order received = minimalSavedOrder(OrderStatus.RECEIVED);
        Order delivered = minimalSavedOrder(OrderStatus.DELIVERED);

        when(orderRepository.findByBranchIdAndShiftId(branchId, shiftId))
                .thenReturn(List.of(received, delivered));
        when(paymentRepository.findByOrderIdIn(any(Collection.class))).thenReturn(List.of());

        List<BackofficeOrderRow> result =
                service.findActiveOrdersByBranch(branchId, shiftId, OrderFilterParams.defaults());

        assertThat(result).hasSize(2);
        verify(orderRepository).findByBranchIdAndShiftId(branchId, shiftId);
        verify(orderRepository, never()).findActiveByBranchId(any(), any());
    }

    // --- transitionStatusForBackoffice ---

    @Test
    void transitionStatusForBackoffice_validDelivery_updatesStatus() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.transitionStatusForBackoffice(orderId, OrderStatus.IN_PREPARATION, null, branch.getId(), 42);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(historyRepository).save(any());
    }

    @Test
    void transitionStatusForBackoffice_validTakeaway_updatesStatus() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.TAKEAWAY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.transitionStatusForBackoffice(orderId, OrderStatus.READY_FOR_PICKUP, null, branch.getId(), 7);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        verify(historyRepository).save(any());
    }

    @Test
    void transitionStatusForBackoffice_invalidTransition_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.TAKEAWAY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                service.transitionStatusForBackoffice(orderId, OrderStatus.DELIVERED, null, branch.getId(), 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválida");
    }

    @Test
    void transitionStatusForBackoffice_wrongBranch_throwsAccessDeniedException() {
        UUID orderId = UUID.randomUUID();
        Branch orderBranch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(orderBranch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        int differentBranchId = 99;
        assertThatThrownBy(() ->
                service.transitionStatusForBackoffice(orderId, OrderStatus.IN_PREPARATION, null, differentBranchId, 5))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findActiveOrdersByBranch_attachesPaymentToMatchingOrder() {
        int branchId = 1;
        Order order = minimalSavedOrder(OrderStatus.RECEIVED);
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(PaymentStatus.APPROVED)
                .method(PaymentMethod.MERCADOPAGO)
                .build();

        when(orderRepository.findActiveByBranchId(any(Integer.class), any(Collection.class)))
                .thenReturn(List.of(order));
        when(paymentRepository.findByOrderIdIn(any(Collection.class))).thenReturn(List.of(payment));

        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(branchId, OrderFilterParams.defaults());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).payment()).isNotNull();
        assertThat(result.get(0).payment().getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    // --- findActiveOrdersByBranch con filtros ---

    private Order orderWithStatus(OrderStatus status, LocalDateTime createdAt) {
        return Order.builder()
                .id(UUID.randomUUID())
                .status(status)
                .orderType(OrderType.TAKEAWAY)
                .totalAmount(BigDecimal.TEN)
                .branch(branch(tenant()))
                .items(List.of())
                .createdAt(createdAt)
                .build();
    }

    private void stubRepoAndPayments(int branchId, List<Order> orders) {
        when(orderRepository.findActiveByBranchId(any(Integer.class), any(Collection.class)))
                .thenReturn(orders);
        when(paymentRepository.findByOrderIdIn(any(Collection.class))).thenReturn(List.of());
    }

    @Test
    void findActiveOrdersByBranch_statusFilter_returnsOnlyMatchingStatus() {
        LocalDateTime now = LocalDateTime.now();
        Order received = orderWithStatus(OrderStatus.RECEIVED, now);
        Order inPrep = orderWithStatus(OrderStatus.IN_PREPARATION, now);
        stubRepoAndPayments(1, List.of(received, inPrep));

        OrderFilterParams params = new OrderFilterParams(OrderStatus.RECEIVED, null, null, null);
        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(1, params);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).order().getStatus()).isEqualTo(OrderStatus.RECEIVED);
    }

    @Test
    void findActiveOrdersByBranch_dateFromFilter_excludesOrdersBefore() {
        LocalDateTime base = LocalDateTime.of(2024, 6, 1, 12, 0);
        Order old = orderWithStatus(OrderStatus.RECEIVED, base.minusDays(1));
        Order recent = orderWithStatus(OrderStatus.RECEIVED, base.plusDays(1));
        stubRepoAndPayments(1, List.of(old, recent));

        OrderFilterParams params = new OrderFilterParams(null, base, null, null);
        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(1, params);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).order().getCreatedAt()).isEqualTo(recent.getCreatedAt());
    }

    @Test
    void findActiveOrdersByBranch_dateToFilter_excludesOrdersAfter() {
        LocalDateTime base = LocalDateTime.of(2024, 6, 1, 12, 0);
        Order old = orderWithStatus(OrderStatus.RECEIVED, base.minusDays(1));
        Order future = orderWithStatus(OrderStatus.RECEIVED, base.plusDays(1));
        stubRepoAndPayments(1, List.of(old, future));

        OrderFilterParams params = new OrderFilterParams(null, null, base, null);
        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(1, params);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).order().getCreatedAt()).isEqualTo(old.getCreatedAt());
    }

    @Test
    void findActiveOrdersByBranch_combinedFilters_returnsOnlyCorrect() {
        LocalDateTime base = LocalDateTime.of(2024, 6, 1, 12, 0);
        Order match = orderWithStatus(OrderStatus.RECEIVED, base);
        Order wrongStatus = orderWithStatus(OrderStatus.IN_PREPARATION, base);
        Order tooOld = orderWithStatus(OrderStatus.RECEIVED, base.minusDays(2));
        Order tooNew = orderWithStatus(OrderStatus.RECEIVED, base.plusDays(2));
        stubRepoAndPayments(1, List.of(match, wrongStatus, tooOld, tooNew));

        OrderFilterParams params = new OrderFilterParams(
                OrderStatus.RECEIVED,
                base.minusDays(1),
                base.plusDays(1),
                null
        );
        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(1, params);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).order().getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(result.get(0).order().getCreatedAt()).isEqualTo(match.getCreatedAt());
    }

    @Test
    void findActiveOrdersByBranch_orderByAsc_returnsSortedAscending() {
        LocalDateTime base = LocalDateTime.of(2024, 6, 1, 12, 0);
        Order first = orderWithStatus(OrderStatus.RECEIVED, base);
        Order second = orderWithStatus(OrderStatus.RECEIVED, base.plusHours(1));
        Order third = orderWithStatus(OrderStatus.RECEIVED, base.plusHours(2));
        stubRepoAndPayments(1, List.of(third, first, second));

        OrderFilterParams params = new OrderFilterParams(null, null, null, OrderFilterParams.ORDER_ASC);
        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(1, params);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).order().getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(result.get(1).order().getCreatedAt()).isEqualTo(second.getCreatedAt());
        assertThat(result.get(2).order().getCreatedAt()).isEqualTo(third.getCreatedAt());
    }

    @Test
    void findActiveOrdersByBranch_defaultOrder_returnsSortedDescending() {
        LocalDateTime base = LocalDateTime.of(2024, 6, 1, 12, 0);
        Order first = orderWithStatus(OrderStatus.RECEIVED, base);
        Order second = orderWithStatus(OrderStatus.RECEIVED, base.plusHours(1));
        Order third = orderWithStatus(OrderStatus.RECEIVED, base.plusHours(2));
        stubRepoAndPayments(1, List.of(first, second, third));

        List<BackofficeOrderRow> result = service.findActiveOrdersByBranch(1, OrderFilterParams.defaults());

        assertThat(result).hasSize(3);
        assertThat(result.get(0).order().getCreatedAt()).isEqualTo(third.getCreatedAt());
        assertThat(result.get(2).order().getCreatedAt()).isEqualTo(first.getCreatedAt());
    }

    // --- getOrderDetailForBackoffice ---

    @Test
    void getOrderDetailForBackoffice_returnsCompleteDetail() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Product product = product(new BigDecimal("1500.00"));

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("1500.00"))
                .subtotal(new BigDecimal("3000.00"))
                .build();

        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.TAKEAWAY)
                .origin(OrderOrigin.CLIENT)
                .subtotal(new BigDecimal("3000.00"))
                .deliveryFee(BigDecimal.ZERO)
                .serviceFee(new BigDecimal("200.00"))
                .totalAmount(new BigDecimal("3200.00"))
                .branch(branch)
                .items(List.of(item))
                .build();

        OrderStatusHistory historyEntry = OrderStatusHistory.builder()
                .id(UUID.randomUUID())
                .order(order)
                .fromStatus(null)
                .toStatus(OrderStatus.RECEIVED)
                .changedAt(LocalDateTime.now())
                .build();

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(PaymentStatus.APPROVED)
                .method(PaymentMethod.MERCADOPAGO)
                .build();

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByChangedAtAsc(orderId)).thenReturn(List.of(historyEntry));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        BackofficeOrderDetail result = service.getOrderDetailForBackoffice(orderId, branch.getId());

        assertThat(result.order().getId()).isEqualTo(orderId);
        assertThat(result.order().getItems()).hasSize(1);
        assertThat(result.history()).hasSize(1);
        assertThat(result.payment()).isNotNull();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    // --- findOrderHistoryByBranch ---

    @Test
    void findOrderHistoryByBranch_returnsOnlyTerminalOrdersPaginated() {
        LocalDateTime now = LocalDateTime.now();
        Order delivered = orderWithStatus(OrderStatus.DELIVERED, now.minusHours(2));
        Order cancelled = orderWithStatus(OrderStatus.CANCELLED, now.minusHours(1));

        PageImpl<Order> pageResult = new PageImpl<>(List.of(cancelled, delivered));

        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageResult);
        when(orderRepository.findByIdsWithItems(any(Collection.class)))
                .thenReturn(List.of(cancelled, delivered));
        when(paymentRepository.findByOrderIdIn(any(Collection.class)))
                .thenReturn(List.of());

        Page<BackofficeOrderRow> result = service.findOrderHistoryByBranch(1, null, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(row ->
                row.order().getStatus() == OrderStatus.DELIVERED ||
                row.order().getStatus() == OrderStatus.CANCELLED);
    }

    @Test
    void findOrderHistoryByBranch_emptyResult_returnsEmptyPage() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<BackofficeOrderRow> result = service.findOrderHistoryByBranch(1, null, null, null, 0, 20);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // --- transitionToPreviousStatusForBackoffice ---

    @Test
    void transitionToPreviousStatus_deliveryOnTheWay_revertsToInPreparation() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.ON_THE_WAY)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.transitionToPreviousStatusForBackoffice(orderId, branch.getId(), 10);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(historyRepository).save(any());
    }

    @Test
    void transitionToPreviousStatus_takeawayReadyForPickup_revertsToInPreparation() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.READY_FOR_PICKUP)
                .orderType(OrderType.TAKEAWAY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.transitionToPreviousStatusForBackoffice(orderId, branch.getId(), 11);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(historyRepository).save(any());
    }

    @Test
    void transitionToPreviousStatus_fromReceived_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                service.transitionToPreviousStatusForBackoffice(orderId, branch.getId(), 12))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RECEIVED");
    }

    @Test
    void transitionToPreviousStatus_fromDelivered_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.DELIVERED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                service.transitionToPreviousStatusForBackoffice(orderId, branch.getId(), 13))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void getOrderDetailForBackoffice_wrongBranch_throwsAccessDeniedException() {
        UUID orderId = UUID.randomUUID();
        Branch orderBranch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.TAKEAWAY)
                .branch(orderBranch)
                .items(List.of())
                .build();

        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                service.getOrderDetailForBackoffice(orderId, 99))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- cancelOrder (US-06-01, US-06-03) ---

    @Test
    void cancelOrder_fromReceived_transitionsToCancelledAndRecordsHistory() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch(tenant()))
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(orderId, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(historyRepository).save(any());
    }

    @Test
    void cancelOrder_fromInPreparation_transitionsToCancellationRequested() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(orderId, "Pedido en preparación");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);
        verify(orderRepository).save(order);
        verify(historyRepository).save(any());
    }

    @Test
    void cancelOrder_fromOnTheWay_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.ON_THE_WAY)
                .orderType(OrderType.DELIVERY)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(orderId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El pedido no puede cancelarse en este estado");
    }

    @Test
    void cancelOrder_fromReadyForPickup_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.READY_FOR_PICKUP)
                .orderType(OrderType.TAKEAWAY)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(orderId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El pedido no puede cancelarse en este estado");
    }

    @Test
    void cancelOrder_orderNotFound_throwsOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelOrder(orderId, null))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // --- resolveCancellationRequest (US-06-02) ---

    @Test
    void resolveCancellationRequest_approve_transitionsToCancelled() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.CANCELLATION_REQUESTED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolveCancellationRequest(orderId, "APPROVE", branch.getId(), 10);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(historyRepository).save(any());
    }

    @Test
    void resolveCancellationRequest_reject_revertsToInPreparation() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.CANCELLATION_REQUESTED)
                .orderType(OrderType.TAKEAWAY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolveCancellationRequest(orderId, "REJECT", branch.getId(), 7);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(orderRepository).save(order);
        verify(historyRepository).save(any());
    }

    @Test
    void resolveCancellationRequest_notInCancellationRequested_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.resolveCancellationRequest(orderId, "APPROVE", branch.getId(), 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cancelación solicitada");
    }

    @Test
    void resolveCancellationRequest_wrongBranch_throwsAccessDeniedException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.CANCELLATION_REQUESTED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.resolveCancellationRequest(orderId, "APPROVE", 99, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolveCancellationRequest_invalidAction_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.CANCELLATION_REQUESTED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.resolveCancellationRequest(orderId, "HOLD", branch.getId(), 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("APPROVE o REJECT");
    }

    @Test
    void resolveCancellationRequest_orderNotFound_throwsOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCancellationRequest(orderId, "APPROVE", 1, 10))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // --- cancelOrder: validación de reason ---

    @Test
    void cancelOrder_directCancel_withoutReason_succeeds() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch(tenant()))
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(orderId, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_directCancel_withReason_persistsReasonInHistory() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch(tenant()))
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(orderId, "me equivoqué");

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getCancellationReason()).isEqualTo("me equivoqué");
        assertThat(captor.getValue().getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_cancellationRequest_withReason_succeeds() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(orderId, "ya no quiero el pedido");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);
    }

    @Test
    void cancelOrder_cancellationRequest_withNullReason_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.DELIVERY)
                .branch(branch(tenant()))
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(orderId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo de cancelación");
    }

    @Test
    void cancelOrder_cancellationRequest_withBlankReason_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.IN_PREPARATION)
                .orderType(OrderType.DELIVERY)
                .branch(branch(tenant()))
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(orderId, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo de cancelación");
    }

    // --- transitionStatusForBackoffice: validación de reason al cancelar ---

    @Test
    void transitionStatusForBackoffice_cancel_withReason_persistsReasonInHistory() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transitionStatusForBackoffice(orderId, OrderStatus.CANCELLED, "sin stock", branch.getId(), 5);

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getCancellationReason()).isEqualTo("sin stock");
        assertThat(captor.getValue().getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void transitionStatusForBackoffice_cancel_withNullReason_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                service.transitionStatusForBackoffice(orderId, OrderStatus.CANCELLED, null, branch.getId(), 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo de cancelación");
    }

    @Test
    void transitionStatusForBackoffice_cancel_withBlankReason_throwsBusinessException() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                service.transitionStatusForBackoffice(orderId, OrderStatus.CANCELLED, "  ", branch.getId(), 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo de cancelación");
    }

    @Test
    void transitionStatusForBackoffice_nonCancelTransition_reasonNotRequired() {
        UUID orderId = UUID.randomUUID();
        Branch branch = branch(tenant());
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.RECEIVED)
                .orderType(OrderType.DELIVERY)
                .branch(branch)
                .build();

        when(orderRepository.findByIdWithBranch(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = service.transitionStatusForBackoffice(orderId, OrderStatus.IN_PREPARATION, null, branch.getId(), 5);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
    }
}
