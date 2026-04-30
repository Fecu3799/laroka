package com.laroka.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.BranchProduct;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.repository.BranchProductRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.order.domain.OrderStateMachine;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderOrigin;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.exception.OrderNotFoundException;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.shared.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BranchProductRepository branchProductRepository;
    @Mock private IdempotencyStore idempotencyStore;

    @InjectMocks
    private OrderService service;

    // --- helpers ---

    private Pizzeria pizzeria() {
        return Pizzeria.builder().id(1).name("LaRoka").build();
    }

    private Branch branch(Pizzeria pizzeria) {
        return Branch.builder()
                .id(1).name("Playa Unión").address("Av. Roca 123").pizzeria(pizzeria)
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
                .branch(branch(pizzeria()))
                .items(List.of())
                .build();
    }

    private void stubBaseCreation(Branch branch, Product product) {
        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(branchProductRepository.findByBranchIdAndProductId(branch.getId(), product.getId()))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findByIdWithDetails(any())).thenReturn(Optional.of(minimalSavedOrder(OrderStatus.PENDING_PAYMENT)));
    }

    // --- createOrder: cálculo de total ---

    @Test
    void createOrder_withBasePrice_usesProductPriceInTotal() {
        Pizzeria p = pizzeria();
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
        Pizzeria p = pizzeria();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));

        BranchProduct bp = BranchProduct.builder()
                .branch(branch).product(product)
                .available(true).priceOverride(new BigDecimal("3100.00"))
                .build();

        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(branchProductRepository.findByBranchIdAndProductId(1, 1)).thenReturn(Optional.of(bp));
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
        Pizzeria p = pizzeria();
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
        Pizzeria p = pizzeria();
        Branch branch = branch(p);
        Product product = product(new BigDecimal("2800.00"));
        stubBaseCreation(branch, product);

        service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-t1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryFee()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("3000.00");
    }

    // --- createOrder: validaciones ---

    @Test
    void createOrder_emptyItems_throwsBusinessException() {
        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        Branch branch = branch(pizzeria());

        assertThatThrownBy(() ->
                service.createOrder(takeawayOrder(branch), List.of(), PaymentMethod.MERCADOPAGO, "key-3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ítem");
    }

    @Test
    void createOrder_branchNotFound_throwsBranchNotFoundException() {
        when(idempotencyStore.get(any())).thenReturn(Optional.empty());
        when(branchRepository.findById(1)).thenReturn(Optional.empty());
        Branch branch = branch(pizzeria());

        assertThatThrownBy(() ->
                service.createOrder(takeawayOrder(branch), List.of(itemFor(1, 1)), PaymentMethod.MERCADOPAGO, "key-4"))
                .isInstanceOf(BranchNotFoundException.class);
    }

    // --- createOrder: idempotencia ---

    @Test
    void createOrder_duplicateIdempotencyKey_returnsCachedOrderWithoutCreatingNew() {
        Order cached = minimalSavedOrder(OrderStatus.RECEIVED);
        when(idempotencyStore.get("dup-key")).thenReturn(Optional.of(cached));

        Branch branch = branch(pizzeria());
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
}
