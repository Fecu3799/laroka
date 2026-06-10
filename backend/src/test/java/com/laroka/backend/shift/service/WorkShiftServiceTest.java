package com.laroka.backend.shift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.repository.PaymentRepository;
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
        when(orderRepository.findByBranch_IdAndStatusInAndCreatedAtBetween(
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
        when(orderRepository.findByBranch_IdAndStatusInAndCreatedAtBetween(
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
}
