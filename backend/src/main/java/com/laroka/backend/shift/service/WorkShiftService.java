package com.laroka.backend.shift.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.shared.exception.BusinessException;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkShiftService {

    private final WorkShiftRepository workShiftRepository;
    private final WorkShiftSummaryRepository workShiftSummaryRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StaffUserRepository staffUserRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public OpenShiftResult openShift(Integer branchId, Integer userId) {
        StaffUser opener = staffUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new BranchNotFoundException(branchId));

        boolean previousShiftClosed = false;
        Optional<WorkShift> existing = workShiftRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN);
        if (existing.isPresent()) {
            closeShiftInternal(existing.get(), opener);
            previousShiftClosed = true;
        }

        WorkShift newShift = WorkShift.builder()
            .branch(branch)
            .openedBy(opener)
            .openedAt(OffsetDateTime.now())
            .status(ShiftStatus.OPEN)
            .build();

        return new OpenShiftResult(workShiftRepository.save(newShift), previousShiftClosed);
    }

    @Transactional
    public WorkShiftSummary closeShift(Integer branchId, Integer userId) {
        StaffUser closer = staffUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        WorkShift shift = workShiftRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN)
            .orElseThrow(() -> new BusinessException("No hay turno activo para esta sucursal"));
        return closeShiftInternal(shift, closer);
    }

    WorkShiftSummary closeShiftInternal(WorkShift shift, StaffUser closer) {
        WorkShiftSummary summary = calculateSummary(shift);
        WorkShiftSummary saved = workShiftSummaryRepository.save(summary);
        shift.setClosedAt(OffsetDateTime.now());
        shift.setClosedBy(closer);
        shift.setStatus(ShiftStatus.CLOSED);
        workShiftRepository.save(shift);
        return saved;
    }

    private WorkShiftSummary calculateSummary(WorkShift shift) {
        Integer branchId = shift.getBranch().getId();
        LocalDateTime from = shift.getOpenedAt().toLocalDateTime();
        LocalDateTime to = LocalDateTime.now();

        List<Order> orders = orderRepository.findByBranch_IdAndStatusInAndCreatedAtBetween(
            branchId,
            List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            from, to
        );

        List<Order> delivered = orders.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .toList();

        BigDecimal totalRevenue = delivered.stream()
            .map(Order::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<PaymentMethod, BigDecimal> revenueByMethod = new EnumMap<>(PaymentMethod.class);
        if (!delivered.isEmpty()) {
            List<UUID> deliveredIds = delivered.stream().map(Order::getId).toList();
            List<Payment> payments = paymentRepository.findByOrderIdIn(deliveredIds);
            Map<UUID, PaymentMethod> methodByOrderId = payments.stream()
                .collect(java.util.stream.Collectors.toMap(
                    p -> p.getOrder().getId(),
                    Payment::getMethod
                ));
            for (Order order : delivered) {
                PaymentMethod method = methodByOrderId.get(order.getId());
                if (method != null) {
                    revenueByMethod.merge(method, order.getTotalAmount(), BigDecimal::add);
                }
            }
        }

        int deliveredCount = delivered.size();
        BigDecimal averageTicket = deliveredCount == 0
            ? BigDecimal.ZERO
            : totalRevenue.divide(BigDecimal.valueOf(deliveredCount), 2, RoundingMode.HALF_UP);

        return WorkShiftSummary.builder()
            .shift(shift)
            .totalOrders(orders.size())
            .deliveredOrders(deliveredCount)
            .cancelledOrders((int) orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count())
            .totalRevenue(totalRevenue)
            .cashRevenue(revenueByMethod.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO))
            .mpRevenue(revenueByMethod.getOrDefault(PaymentMethod.MERCADOPAGO, BigDecimal.ZERO))
            .qrRevenue(revenueByMethod.getOrDefault(PaymentMethod.QR_CODE, BigDecimal.ZERO))
            .averageTicket(averageTicket)
            .build();
    }
}
