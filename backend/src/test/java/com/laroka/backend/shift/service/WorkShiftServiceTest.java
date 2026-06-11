package com.laroka.backend.shift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
        when(orderRepository.findByBranchIdAndStatusInAndCreatedAtBetween(
            eq(1), anyCollection(), any(), any()))
            .thenReturn(List.of(deliveredOrder));
        when(paymentRepository.findByOrderIdIn(anyCollection()))
            .thenReturn(List.of(payment));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));

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
    void openShift_withExistingShiftAndNoOrders_summaryIsAllZero() {
        Branch branch = branch();
        StaffUser opener = staffUser();
        WorkShift existing = openShift(branch, opener);

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(opener));
        when(branchRepository.findById(1)).thenReturn(Optional.of(branch));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(existing));
        when(orderRepository.findByBranchIdAndStatusInAndCreatedAtBetween(
            eq(1), anyCollection(), any(), any()))
            .thenReturn(List.of());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));

        workShiftService.openShift(1, 10);

        ArgumentCaptor<WorkShiftSummary> captor = ArgumentCaptor.forClass(WorkShiftSummary.class);
        verify(workShiftSummaryRepository).save(captor.capture());
        WorkShiftSummary summary = captor.getValue();
        assertThat(summary.getTotalOrders()).isZero();
        assertThat(summary.getDeliveredOrders()).isZero();
        assertThat(summary.getCancelledOrders()).isZero();
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getAverageTicket()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(paymentRepository, never()).findByOrderIdIn(anyCollection());
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
        when(orderRepository.findByBranchIdAndStatusInAndCreatedAtBetween(
            eq(1), anyCollection(), any(), any()))
            .thenReturn(List.of(delivered1, delivered2, cancelled));
        when(paymentRepository.findByOrderIdIn(anyCollection()))
            .thenReturn(List.of(payment1, payment2));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workShiftSummaryRepository.save(any(WorkShiftSummary.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkShiftSummary result = workShiftService.closeShift(1, 10);

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
    void closeShift_withNoOrders_summaryIsAllZero() {
        Branch branch = branch();
        StaffUser closer = staffUser();
        WorkShift shift = openShift(branch, closer);

        when(staffUserRepository.findById(10)).thenReturn(Optional.of(closer));
        when(workShiftRepository.findByBranchIdAndStatus(1, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
        when(orderRepository.findByBranchIdAndStatusInAndCreatedAtBetween(
            eq(1), anyCollection(), any(), any()))
            .thenReturn(List.of());
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workShiftSummaryRepository.save(any(WorkShiftSummary.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkShiftSummary result = workShiftService.closeShift(1, 10);

        assertThat(result.getTotalOrders()).isZero();
        assertThat(result.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAverageTicket()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(paymentRepository, never()).findByOrderIdIn(anyCollection());
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
        when(orderItemRepository.findTopProducts(eq(1), eq(OrderStatus.DELIVERED), any(), any(), any()))
            .thenReturn(rawRows);

        List<TopProductDTO> result = workShiftService.getTopProducts(shiftId, 1);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductId()).isEqualTo(10);
        assertThat(result.get(0).getProductName()).isEqualTo("Pizza Muzzarella");
        assertThat(result.get(0).getQuantitySold()).isEqualTo(8L);
        assertThat(result.get(1).getProductName()).isEqualTo("Fugazza");
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
}
