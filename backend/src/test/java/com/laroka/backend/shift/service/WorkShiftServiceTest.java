package com.laroka.backend.shift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.shared.exception.BusinessException;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.repository.OrderItemRepository;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.repository.PaymentRepository;
import com.laroka.backend.shift.dto.TopProductDTO;
import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.entity.WorkShiftSummary;
import com.laroka.backend.shift.repository.WorkShiftRepository;
import com.laroka.backend.shift.repository.WorkShiftSummaryRepository;
import com.laroka.backend.staffuser.entity.StaffUser;
import com.laroka.backend.staffuser.repository.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class WorkShiftServiceTest {

    @Mock private WorkShiftRepository workShiftRepository;
    @Mock private WorkShiftSummaryRepository workShiftSummaryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StaffUserRepository staffUserRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private com.laroka.backend.order.service.OrderService orderService;

    @InjectMocks
    private WorkShiftService workShiftService;

    private Branch branch() {
        return Branch.builder().id(1).name("Playa Unión").build();
    }

    private StaffUser staffUser() {
        return StaffUser.builder().id(10).name("Manager 1").branch(branch()).build();
    }

    private WorkShift openShift(Branch branch, StaffUser opener) {
        return WorkShift.builder()
            .id(UUID.randomUUID())
            .branch(branch)
            .openedBy(opener)
            .openedAt(OffsetDateTime.now().minusHours(3))
            .status(ShiftStatus.OPEN)
            .build();
    }

    @Test
    void openShift_withoutPreviousShift_createsNewShiftAndFlagFalse() {
        Branch branch = branch();
        StaffUser opener = staffUser();

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(opener));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN)).thenReturn(Optional.empty());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));

        OpenShiftResult result = workShiftService.openShift(1, 10);

        assertThat(result.previousShiftClosed()).isFalse();
        assertThat(result.shift().getStatus()).isEqualTo(ShiftStatus.OPEN);
        assertThat(result.shift().getBranch()).isEqualTo(branch);
        assertThat(result.shift().getOpenedBy()).isEqualTo(opener);
        verify(workShiftSummaryRepository, never()).save(any());
    }

    @Test
    void openShift_inactiveBranch_throwsBusinessException() {
        Branch branch = Branch.builder().id(1).name("Playa Unión").active(false).build();
        StaffUser opener = staffUser();

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(opener));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> workShiftService.openShift(1, 10))
            .isInstanceOf(BusinessException.class)
            .hasMessage("No se puede abrir turno en una sucursal desactivada");

        verify(workShiftRepository, never()).save(any());
    }

    @Test
    void openShift_activeBranch_createsShift() {
        Branch branch = Branch.builder().id(1).name("Playa Unión").active(true).build();
        StaffUser opener = staffUser();

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(opener));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN)).thenReturn(Optional.empty());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));

        OpenShiftResult result = workShiftService.openShift(1, 10);

        assertThat(result.shift().getStatus()).isEqualTo(ShiftStatus.OPEN);
        verify(workShiftRepository).save(any(WorkShift.class));
    }

    @Test
    void openShift_withExistingOpenShift_closesPreviousAndCreatesNew() {
        Branch branch = branch();
        StaffUser opener = staffUser();
        WorkShift existing = openShift(branch, opener);

        UUID deliveredOrderId = UUID.randomUUID();
        Order deliveredOrder = Order.builder()
            .id(deliveredOrderId)
            .status(OrderStatus.DELIVERED)
            .orderType(OrderType.DELIVERY)
            .totalAmount(new BigDecimal("1500.00"))
            .build();
        Payment payment = Payment.builder()
            .method(PaymentMethod.CASH)
            .order(deliveredOrder)
            .build();

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(opener));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(existing));
        when(orderRepository.findByShiftIdAndStatusIn(
            eq(existing.getId()), anyCollection()))
            .thenReturn(List.of(deliveredOrder));
        when(paymentRepository.findByOrderIdIn(anyCollection()))
            .thenReturn(List.of(payment));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workShiftSummaryRepository.save(any(WorkShiftSummary.class))).thenAnswer(inv -> inv.getArgument(0));

        OpenShiftResult result = workShiftService.openShift(1, 10);

        assertThat(result.previousShiftClosed()).isTrue();
        assertThat(result.shift().getStatus()).isEqualTo(ShiftStatus.OPEN);

        ArgumentCaptor<WorkShiftSummary> captor = ArgumentCaptor.forClass(WorkShiftSummary.class);
        verify(workShiftSummaryRepository).save(captor.capture());
        WorkShiftSummary summary = captor.getValue();
        assertThat(summary.getDeliveredOrders()).isEqualTo(1);
        assertThat(summary.getCancelledOrders()).isZero();
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("1500.00");
        assertThat(summary.getCashRevenue()).isEqualByComparingTo("1500.00");
        assertThat(summary.getMpRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getQrRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getAverageTicket()).isEqualByComparingTo("1500.00");
        assertThat(summary.getDeliveryOrders()).isEqualTo(1);
        assertThat(summary.getTakeawayOrders()).isZero();
        assertThat(summary.getCancellationRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void openShift_withEmptyPreviousShift_deletesPreviousAndCreatesNew() {
        Branch branch = branch();
        StaffUser opener = staffUser();
        WorkShift existing = openShift(branch, opener);

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(opener));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(existing));
        when(orderRepository.findByShiftIdAndStatusIn(
            eq(existing.getId()), anyCollection()))
            .thenReturn(List.of());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));

        OpenShiftResult result = workShiftService.openShift(1, 10);

        // Turno previo sin actividad: se elimina y NO se avisa cierre de turno previo.
        assertThat(result.previousShiftClosed()).isFalse();
        assertThat(result.shift().getStatus()).isEqualTo(ShiftStatus.OPEN);
        verify(workShiftRepository).delete(existing);
        verify(workShiftSummaryRepository, never()).save(any());
        // Aun eliminando el turno vacío, la recepción de pedidos queda deshabilitada.
        verify(branchRepository).updateAcceptingOrders(1, false);
    }

    @Test
    void closeShift_withMixedOrders_calculatesCorrectSummary() {
        Branch branch = branch();
        StaffUser closer = staffUser();
        WorkShift shift = openShift(branch, closer);

        UUID deliveredId1 = UUID.randomUUID();
        UUID deliveredId2 = UUID.randomUUID();
        UUID cancelledId = UUID.randomUUID();

        Order delivered1 = Order.builder().id(deliveredId1).status(OrderStatus.DELIVERED)
            .orderType(OrderType.DELIVERY).totalAmount(new BigDecimal("900.00")).build();
        Order delivered2 = Order.builder().id(deliveredId2).status(OrderStatus.DELIVERED)
            .orderType(OrderType.TAKEAWAY).totalAmount(new BigDecimal("600.00")).build();
        Order cancelled = Order.builder().id(cancelledId).status(OrderStatus.CANCELLED)
            .totalAmount(new BigDecimal("300.00")).build();

        Payment payment1 = Payment.builder().method(PaymentMethod.CASH).order(delivered1).build();
        Payment payment2 = Payment.builder().method(PaymentMethod.MERCADOPAGO).order(delivered2).build();

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(closer));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(orderRepository.findByShiftIdAndStatusIn(
            eq(shift.getId()), anyCollection()))
            .thenReturn(List.of(delivered1, delivered2, cancelled));
        when(paymentRepository.findByOrderIdIn(anyCollection()))
            .thenReturn(List.of(payment1, payment2));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workShiftSummaryRepository.save(any(WorkShiftSummary.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkShiftSummary result = workShiftService.closeShift(1, 10).summary();

        assertThat(result.getTotalOrders()).isEqualTo(3);
        assertThat(result.getDeliveredOrders()).isEqualTo(2);
        assertThat(result.getCancelledOrders()).isEqualTo(1);
        assertThat(result.getTotalRevenue()).isEqualByComparingTo("1500.00");
        assertThat(result.getCashRevenue()).isEqualByComparingTo("900.00");
        assertThat(result.getMpRevenue()).isEqualByComparingTo("600.00");
        assertThat(result.getQrRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAverageTicket()).isEqualByComparingTo("750.00");
        assertThat(result.getDeliveryOrders()).isEqualTo(1);
        assertThat(result.getTakeawayOrders()).isEqualTo(1);
        // cancellationRate: 1 cancelled / 3 total * 100 = 33.33
        assertThat(result.getCancellationRate()).isEqualByComparingTo("33.33");
    }

    @Test
    void closeShift_emptyShift_deletesShiftAndReturnsWasEmpty() {
        Branch branch = branch();
        StaffUser closer = staffUser();
        WorkShift shift = openShift(branch, closer);

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(closer));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(orderRepository.findByShiftIdAndStatusIn(
            eq(shift.getId()), anyCollection()))
            .thenReturn(List.of());

        // No lanza excepción: devuelve wasEmpty=true para que la transacción
        // confirme el delete del turno vacío en lugar de revertirlo.
        CloseShiftResult result = workShiftService.closeShift(1, 10);

        assertThat(result.wasEmpty()).isTrue();
        assertThat(result.summary()).isNull();
        verify(workShiftRepository).delete(shift);
        verify(workShiftSummaryRepository, never()).save(any());
        verify(branchRepository).updateAcceptingOrders(1, false);
    }

    @Test
    void getCurrentShift_activeShift_returnsShift() {
        Branch branch = branch();
        StaffUser opener = staffUser();
        WorkShift shift = openShift(branch, opener);

        when(workShiftRepository.findByBranchIdAndStatusWithOpenedBy(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));

        Optional<WorkShift> result = workShiftService.getCurrentShift(1);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ShiftStatus.OPEN);
        assertThat(result.get().getOpenedBy().getName()).isEqualTo("Manager 1");
    }

    @Test
    void getCurrentShift_noActiveShift_returnsEmpty() {
        when(workShiftRepository.findByBranchIdAndStatusWithOpenedBy(1, ShiftStatus.OPEN))
            .thenReturn(Optional.empty());

        Optional<WorkShift> result = workShiftService.getCurrentShift(1);

        assertThat(result).isEmpty();
    }

    @Test
    void getShiftHistory_twoClosedShifts_returnsPageCorrectlyMapped() {
        Branch branch = branch();
        StaffUser opener = staffUser();
        OffsetDateTime now = OffsetDateTime.now();

        WorkShift shift1 = WorkShift.builder()
            .id(UUID.randomUUID()).branch(branch).openedBy(opener).closedBy(opener)
            .openedAt(now.minusHours(10)).closedAt(now.minusHours(2))
            .status(ShiftStatus.CLOSED).build();
        WorkShift shift2 = WorkShift.builder()
            .id(UUID.randomUUID()).branch(branch).openedBy(opener).closedBy(opener)
            .openedAt(now.minusHours(25)).closedAt(now.minusHours(13))
            .status(ShiftStatus.CLOSED).build();

        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "closedAt"));
        Page<WorkShift> pageResult = new PageImpl<>(List.of(shift1, shift2), pageable, 2);

        when(workShiftRepository.findByBranchIdAndStatus(eq(1), eq(ShiftStatus.CLOSED), any(PageRequest.class)))
            .thenReturn(pageResult);

        Page<WorkShift> result = workShiftService.getShiftHistory(1, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).containsExactly(shift1, shift2);
        assertThat(result.getContent().get(0).getClosedAt()).isAfter(result.getContent().get(1).getClosedAt());
    }

    @Test
    void closeShift_noActiveShift_throwsBusinessException() {
        StaffUser closer = staffUser();

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(closer));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workShiftService.closeShift(1, 10))
            .isInstanceOf(BusinessException.class)
            .hasMessage("No hay turno activo para esta sucursal");
    }

    @Test
    void closeShift_acceptingOrdersActive_throwsBusinessException() {
        Branch branch = Branch.builder().id(1).name("Playa Unión").acceptingOrders(true).build();
        StaffUser closer = staffUser();
        WorkShift shift = openShift(branch, closer);

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(closer));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> workShiftService.closeShift(1, 10))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Desactivá la recepción de pedidos antes de cerrar el turno");

        verify(workShiftSummaryRepository, never()).save(any());
        verify(workShiftRepository, never()).delete(any(WorkShift.class));
    }

    @Test
    void closeShift_withActiveOrders_throwsBusinessException() {
        Branch branch = branch(); // acceptingOrders = false
        StaffUser closer = staffUser();
        WorkShift shift = openShift(branch, closer);

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(closer));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(orderRepository.existsByShiftIdAndStatusIn(
            eq(shift.getId()), any()))
            .thenReturn(true);

        assertThatThrownBy(() -> workShiftService.closeShift(1, 10))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Hay pedidos activos sin resolver. Resolválos antes de cerrar el turno.");

        verify(workShiftSummaryRepository, never()).save(any());
        verify(workShiftRepository, never()).delete(any(WorkShift.class));
    }

    @Test
    void getCurrentShiftSummary_activeShiftWithOrders_returnsSummaryWithoutPersisting() {
        Branch branch = branch();
        StaffUser opener = staffUser();
        WorkShift shift = openShift(branch, opener);

        UUID orderId = UUID.randomUUID();
        Order delivered = Order.builder()
            .id(orderId)
            .status(OrderStatus.DELIVERED)
            .orderType(OrderType.DELIVERY)
            .totalAmount(new BigDecimal("1200.00"))
            .build();
        Payment payment = Payment.builder()
            .method(PaymentMethod.CASH)
            .order(delivered)
            .build();

        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(orderRepository.findByShiftIdAndStatusIn(
            eq(shift.getId()), anyCollection()))
            .thenReturn(List.of(delivered));
        when(paymentRepository.findByOrderIdIn(anyCollection()))
            .thenReturn(List.of(payment));

        WorkShiftSummary result = workShiftService.getCurrentShiftSummary(1);

        assertThat(result.getDeliveredOrders()).isEqualTo(1);
        assertThat(result.getCancelledOrders()).isZero();
        assertThat(result.getTotalRevenue()).isEqualByComparingTo("1200.00");
        assertThat(result.getCashRevenue()).isEqualByComparingTo("1200.00");
        assertThat(result.getAverageTicket()).isEqualByComparingTo("1200.00");
        assertThat(result.getDeliveryOrders()).isEqualTo(1);
        assertThat(result.getCalculatedAt()).isNotNull();
        verify(workShiftSummaryRepository, never()).save(any());
    }

    @Test
    void getTopProducts_closedShift_returnsMappedList() {
        Branch branch = branch();
        UUID shiftId = UUID.randomUUID();
        WorkShift shift = WorkShift.builder()
            .id(shiftId)
            .branch(branch)
            .openedAt(OffsetDateTime.now().minusHours(4))
            .closedAt(OffsetDateTime.now().minusHours(1))
            .status(ShiftStatus.CLOSED)
            .build();

        List<Object[]> rawRows = List.of(
            new Object[]{10, "Pizza Muzzarella", 8L},
            new Object[]{11, "Fugazza", 5L}
        );

        when(workShiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(orderItemRepository.findTopProductsByShiftId(eq(shiftId), eq(OrderStatus.DELIVERED), any()))
            .thenReturn(rawRows);

        List<TopProductDTO> result = workShiftService.getTopProducts(shiftId, 1);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductId()).isEqualTo(10);
        assertThat(result.get(0).getProductName()).isEqualTo("Pizza Muzzarella");
        assertThat(result.get(0).getQuantitySold()).isEqualTo(8L);
        assertThat(result.get(1).getProductName()).isEqualTo("Fugazza");
    }

    @Test
    void toggleAcceptingOrders_activeShift_flipsValue() {
        Branch branch = Branch.builder().id(1).name("Playa Unión").acceptingOrders(false).build();
        WorkShift shift = openShift(branch, staffUser());

        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

        boolean result = workShiftService.toggleAcceptingOrders(1);

        assertThat(result).isTrue();
        verify(branchRepository).updateAcceptingOrders(1, true);
    }

    @Test
    void toggleAcceptingOrders_activeShiftAlreadyAccepting_flipsToFalse() {
        Branch branch = Branch.builder().id(1).name("Playa Unión").acceptingOrders(true).build();
        WorkShift shift = openShift(branch, staffUser());

        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

        boolean result = workShiftService.toggleAcceptingOrders(1);

        assertThat(result).isFalse();
        verify(branchRepository).updateAcceptingOrders(1, false);
    }

    @Test
    void toggleAcceptingOrders_noActiveShift_throwsBusinessException() {
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workShiftService.toggleAcceptingOrders(1))
            .isInstanceOf(BusinessException.class)
            .hasMessage("No hay turno activo para esta sucursal");

        verify(branchRepository, never()).updateAcceptingOrders(anyInt(), anyBoolean());
    }

    @Test
    void closeShift_disablesAcceptingOrders() {
        Branch branch = branch();
        StaffUser closer = staffUser();
        WorkShift shift = openShift(branch, closer);

        Order delivered = Order.builder().id(UUID.randomUUID()).status(OrderStatus.DELIVERED)
            .orderType(OrderType.DELIVERY).totalAmount(new BigDecimal("500.00")).build();
        Payment payment = Payment.builder().method(PaymentMethod.CASH).order(delivered).build();

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(closer));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(orderRepository.findByShiftIdAndStatusIn(
            eq(shift.getId()), anyCollection()))
            .thenReturn(List.of(delivered));
        when(paymentRepository.findByOrderIdIn(anyCollection()))
            .thenReturn(List.of(payment));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workShiftSummaryRepository.save(any(WorkShiftSummary.class))).thenAnswer(inv -> inv.getArgument(0));

        workShiftService.closeShift(1, 10);

        verify(branchRepository).updateAcceptingOrders(1, false);
    }

    @Test
    void getTopProducts_wrongBranch_throwsBusinessException() {
        Branch branch = branch(); // id=1
        UUID shiftId = UUID.randomUUID();
        WorkShift shift = WorkShift.builder()
            .id(shiftId)
            .branch(branch)
            .openedAt(OffsetDateTime.now().minusHours(4))
            .closedAt(OffsetDateTime.now().minusHours(1))
            .status(ShiftStatus.CLOSED)
            .build();

        when(workShiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> workShiftService.getTopProducts(shiftId, 99))
            .isInstanceOf(BusinessException.class);
    }

    // ── autoCloseShift ───────────────────────────────────────────────────────────

    @Test
    void autoCloseShift_shiftWithActiveOrdersOnly_closesWithoutDeleting() {
        // Escenario del bug: turno con pedidos activos (RECEIVED/IN_PREP),
        // sin ninguno DELIVERED ni CANCELLED → calculateSummary devuelve totalOrders=0.
        // El auto-cierre NO debe eliminar el turno (viola shift_id NOT NULL en orders).
        Branch branch = branch();
        WorkShift shift = openShift(branch, staffUser());
        UUID shiftId = shift.getId();

        when(workShiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        // Sin pedidos DELIVERED/CANCELLED
        when(orderRepository.findByShiftIdAndStatusIn(eq(shiftId), anyCollection()))
            .thenReturn(List.of());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));

        workShiftService.autoCloseShift(shiftId);

        // El turno debe quedar CLOSED, nunca eliminado
        ArgumentCaptor<WorkShift> captor = ArgumentCaptor.forClass(WorkShift.class);
        verify(workShiftRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ShiftStatus.CLOSED);
        assertThat(captor.getValue().getClosedAt()).isNotNull();
        assertThat(captor.getValue().getClosedBy()).isNull();
        verify(workShiftRepository, never()).delete(any(WorkShift.class));
        // Sin actividad → no se crea summary, pero sí se deshabilita recepción de pedidos
        verify(workShiftSummaryRepository, never()).save(any());
        verify(branchRepository).updateAcceptingOrders(1, false);
    }

    @Test
    void autoCloseShift_shiftWithActivity_closesAndSavesSummary() {
        Branch branch = branch();
        WorkShift shift = openShift(branch, staffUser());
        UUID shiftId = shift.getId();

        Order delivered = Order.builder().id(UUID.randomUUID()).status(OrderStatus.DELIVERED)
            .orderType(OrderType.DELIVERY).totalAmount(new BigDecimal("800.00")).build();
        Payment payment = Payment.builder().method(PaymentMethod.CASH).order(delivered).build();

        when(workShiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(orderRepository.findByShiftIdAndStatusIn(eq(shiftId), anyCollection()))
            .thenReturn(List.of(delivered));
        when(paymentRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of(payment));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workShiftSummaryRepository.save(any(WorkShiftSummary.class))).thenAnswer(inv -> inv.getArgument(0));

        workShiftService.autoCloseShift(shiftId);

        verify(workShiftSummaryRepository).save(any(WorkShiftSummary.class));
        verify(workShiftRepository).save(any(WorkShift.class));
        verify(workShiftRepository, never()).delete(any(WorkShift.class));
        verify(branchRepository).updateAcceptingOrders(1, false);
    }

    @Test
    void autoCloseShift_withActiveOrders_cancelsThemBeforeClosingAndSummaryReflectsThem() {
        // Nueva política: el auto-cierre cancela todos los pedidos activos del turno
        // (delegando en OrderService) ANTES de calcular el summary y cerrar. El
        // summary debe reflejar esos pedidos ya como cancelados.
        Branch branch = branch();
        WorkShift shift = openShift(branch, staffUser());
        UUID shiftId = shift.getId();

        Order cancelled1 = Order.builder().id(UUID.randomUUID()).status(OrderStatus.CANCELLED)
            .orderType(OrderType.DELIVERY).totalAmount(new BigDecimal("1500.00")).build();
        Order cancelled2 = Order.builder().id(UUID.randomUUID()).status(OrderStatus.CANCELLED)
            .orderType(OrderType.TAKEAWAY).totalAmount(new BigDecimal("800.00")).build();

        when(workShiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        // calculateSummary corre DESPUÉS de la cancelación: devuelve los pedidos
        // recién cancelados como CANCELLED terminales.
        when(orderRepository.findByShiftIdAndStatusIn(eq(shiftId), anyCollection()))
            .thenReturn(List.of(cancelled1, cancelled2));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workShiftSummaryRepository.save(any(WorkShiftSummary.class))).thenAnswer(inv -> inv.getArgument(0));

        workShiftService.autoCloseShift(shiftId);

        // La cancelación de pedidos activos ocurre ANTES de persistir el turno cerrado.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(orderService, workShiftRepository);
        inOrder.verify(orderService).cancelActiveOrdersForShiftAutoClose(shiftId);
        inOrder.verify(workShiftRepository).save(any(WorkShift.class));

        // El summary refleja los pedidos recién cancelados.
        ArgumentCaptor<WorkShiftSummary> summaryCaptor = ArgumentCaptor.forClass(WorkShiftSummary.class);
        verify(workShiftSummaryRepository).save(summaryCaptor.capture());
        WorkShiftSummary summary = summaryCaptor.getValue();
        assertThat(summary.getTotalOrders()).isEqualTo(2);
        assertThat(summary.getCancelledOrders()).isEqualTo(2);
        assertThat(summary.getDeliveredOrders()).isZero();
        assertThat(summary.getCancellationRate()).isEqualByComparingTo("100.00");

        // El turno queda CLOSED y la recepción de pedidos deshabilitada.
        ArgumentCaptor<WorkShift> shiftCaptor = ArgumentCaptor.forClass(WorkShift.class);
        verify(workShiftRepository).save(shiftCaptor.capture());
        assertThat(shiftCaptor.getValue().getStatus()).isEqualTo(ShiftStatus.CLOSED);
        verify(workShiftRepository, never()).delete(any(WorkShift.class));
        verify(branchRepository).updateAcceptingOrders(1, false);
    }

    @Test
    void autoCloseShift_alreadyClosed_doesNothing() {
        Branch branch = branch();
        WorkShift shift = WorkShift.builder()
            .id(UUID.randomUUID()).branch(branch)
            .openedAt(OffsetDateTime.now().minusHours(10))
            .closedAt(OffsetDateTime.now().minusHours(1))
            .status(ShiftStatus.CLOSED).build();

        when(workShiftRepository.findById(shift.getId())).thenReturn(Optional.of(shift));

        workShiftService.autoCloseShift(shift.getId());

        verify(workShiftRepository, never()).save(any());
        verify(workShiftSummaryRepository, never()).save(any());
        verify(branchRepository, never()).updateAcceptingOrders(anyInt(), anyBoolean());
    }
}
