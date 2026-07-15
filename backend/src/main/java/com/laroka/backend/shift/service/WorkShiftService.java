package com.laroka.backend.shift.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.shared.exception.BusinessException;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.entity.PaymentMethod;
import com.laroka.backend.order.repository.OrderItemRepository;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.service.OrderService;
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

import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkShiftService {

    private final WorkShiftRepository workShiftRepository;
    private final WorkShiftSummaryRepository workShiftSummaryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final StaffUserRepository staffUserRepository;
    private final BranchRepository branchRepository;
    private final OrderService orderService;

    @Transactional
    public OpenShiftResult openShift(Integer branchId, Integer userId) {
        StaffUser opener = staffUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new BranchNotFoundException(branchId));

        // US-15-10: una sucursal desactivada no puede abrir turno — cierra la
        // ventana por la que una sucursal inactiva volvería a aceptar pedidos.
        if (!branch.isActive()) {
            throw new BusinessException("No se puede abrir turno en una sucursal desactivada");
        }

        boolean previousShiftClosed = false;
        Optional<WorkShift> existing = workShiftRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN);
        if (existing.isPresent()) {
            // Si el turno previo no tuvo actividad, closeShiftInternal lo elimina y
            // retorna null: no corresponde avisar que se cerró un turno previo.
            WorkShiftSummary closedSummary = closeShiftInternal(existing.get(), opener);
            previousShiftClosed = closedSummary != null;
        }

        WorkShift newShift = WorkShift.builder()
            .branch(branch)
            .openedBy(opener)
            .openedAt(OffsetDateTime.now())
            .status(ShiftStatus.OPEN)
            .build();

        return new OpenShiftResult(workShiftRepository.save(newShift), previousShiftClosed);
    }

    public Page<WorkShift> getShiftHistory(Integer branchId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "closedAt"));
        return workShiftRepository.findByBranchIdAndStatus(branchId, ShiftStatus.CLOSED, pageable);
    }

    public Optional<WorkShift> getCurrentShift(Integer branchId) {
        return workShiftRepository.findByBranchIdAndStatusWithOpenedBy(branchId, ShiftStatus.OPEN);
    }

    @Transactional
    public CloseShiftResult closeShift(Integer branchId, Integer userId) {
        StaffUser closer = staffUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        WorkShift shift = workShiftRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN)
            .orElseThrow(() -> new BusinessException("No hay turno activo para esta sucursal"));

        // 1) No permitir cerrar con la recepción de pedidos activa.
        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new BranchNotFoundException(branchId));
        if (branch.isAcceptingOrders()) {
            throw new BusinessException("Desactivá la recepción de pedidos antes de cerrar el turno");
        }

        // 2) No permitir cerrar con pedidos activos sin resolver del turno actual.
        boolean hasActiveOrders = orderRepository.existsByShiftIdAndStatusIn(
            shift.getId(),
            List.of(OrderStatus.RECEIVED, OrderStatus.IN_PREPARATION,
                    OrderStatus.ON_THE_WAY, OrderStatus.READY_FOR_PICKUP));
        if (hasActiveOrders) {
            throw new BusinessException("Hay pedidos activos sin resolver. Resolválos antes de cerrar el turno.");
        }

        WorkShiftSummary summary = closeShiftInternal(shift, closer);
        // 3) Turno sin actividad: closeShiftInternal lo eliminó y devolvió null.
        // No lanzamos excepción —eso revertiría el delete dentro de esta misma
        // transacción—: señalamos con wasEmpty para que la transacción confirme.
        if (summary == null) {
            return new CloseShiftResult(null, true);
        }
        return new CloseShiftResult(summary, false);
    }

    WorkShiftSummary closeShiftInternal(WorkShift shift, StaffUser closer) {
        WorkShiftSummary summary = calculateSummary(shift);
        // Turno sin actividad (delivered + cancelled == 0): no se persiste summary,
        // se elimina el turno y se retorna null para que el caller lo maneje.
        if (summary.getTotalOrders() == 0) {
            // Cerrar turno siempre deshabilita la recepción de pedidos, también
            // cuando el turno vacío se elimina (cubre el cierre forzado al abrir
            // un turno nuevo con un previo vacío y accepting_orders activo).
            branchRepository.updateAcceptingOrders(shift.getBranch().getId(), false);
            workShiftRepository.delete(shift);
            return null;
        }
        WorkShiftSummary saved = workShiftSummaryRepository.save(summary);
        shift.setClosedAt(OffsetDateTime.now());
        shift.setClosedBy(closer);
        shift.setStatus(ShiftStatus.CLOSED);
        workShiftRepository.save(shift);
        // Cerrar turno siempre deshabilita la recepción de pedidos, sin importar
        // el estado actual del toggle (cubre también el cierre forzado al abrir
        // un turno nuevo, ya que openShift delega en este método).
        branchRepository.updateAcceptingOrders(shift.getBranch().getId(), false);
        return saved;
    }

    @Transactional
    public boolean toggleAcceptingOrders(Integer branchId) {
        if (workShiftRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN).isEmpty()) {
            throw new BusinessException("No hay turno activo para esta sucursal");
        }
        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new BranchNotFoundException(branchId));
        boolean next = !branch.isAcceptingOrders();
        // El operador puede activar/desactivar la recepción en cualquier momento
        // mientras haya un turno activo; no hay restricción de horario.
        branchRepository.updateAcceptingOrders(branchId, next);
        return next;
    }

    private WorkShiftSummary calculateSummary(WorkShift shift) {
        List<Order> orders = orderRepository.findByShiftIdAndStatusIn(
            shift.getId(),
            List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED)
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
        int cancelledCount = (int) orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        int totalCount = orders.size();

        BigDecimal averageTicket = deliveredCount == 0
            ? BigDecimal.ZERO
            : totalRevenue.divide(BigDecimal.valueOf(deliveredCount), 2, RoundingMode.HALF_UP);

        int deliveryOrders = (int) delivered.stream().filter(o -> o.getOrderType() == OrderType.DELIVERY).count();
        int takeawayOrders = (int) delivered.stream().filter(o -> o.getOrderType() == OrderType.TAKEAWAY).count();

        BigDecimal cancellationRate = totalCount == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(cancelledCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);

        return WorkShiftSummary.builder()
            .shift(shift)
            .totalOrders(totalCount)
            .deliveredOrders(deliveredCount)
            .cancelledOrders(cancelledCount)
            .totalRevenue(totalRevenue)
            .cashRevenue(revenueByMethod.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO))
            .mpRevenue(revenueByMethod.getOrDefault(PaymentMethod.MERCADOPAGO, BigDecimal.ZERO))
            .qrRevenue(revenueByMethod.getOrDefault(PaymentMethod.QR_CODE, BigDecimal.ZERO))
            .averageTicket(averageTicket)
            .deliveryOrders(deliveryOrders)
            .takeawayOrders(takeawayOrders)
            .cancellationRate(cancellationRate)
            .build();
    }

    public WorkShiftSummary getCurrentShiftSummary(Integer branchId) {
        WorkShift shift = workShiftRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN)
            .orElseThrow(() -> new BusinessException("No hay turno activo"));
        WorkShiftSummary summary = calculateSummary(shift);
        summary.setCalculatedAt(OffsetDateTime.now());
        return summary;
    }

    public List<WorkShift> findExpiredOpenShifts() {
        OffsetDateTime now = OffsetDateTime.now();
        return workShiftRepository.findAllByStatusWithBranch(ShiftStatus.OPEN).stream()
            .filter(s -> s.getOpenedAt().plusMinutes(s.getBranch().getMaxShiftDurationMinutes()).isBefore(now))
            .toList();
    }

    @Transactional
    public void autoCloseShift(UUID shiftId) {
        WorkShift shift = workShiftRepository.findById(shiftId).orElse(null);
        if (shift == null || shift.getStatus() != ShiftStatus.OPEN) {
            return;
        }

        // Política de auto-cierre: a diferencia del cierre manual —que bloquea si
        // hay pedidos activos sin resolver— el auto-cierre cancela automáticamente
        // todo pedido activo del turno antes de cerrarlo. Así ninguno queda
        // huérfano referenciando un turno CLOSED ni atrasado e invisible al abrir
        // el turno siguiente. Los pagos MercadoPago aprobados se reembolsan en su
        // totalidad. El summary se calcula recién después, con estos pedidos ya
        // contabilizados como cancelados. Ver docs/KNOWN_ISSUES.md.
        orderService.cancelActiveOrdersForShiftAutoClose(shiftId);

        // Nunca eliminar el turno en cierre automático — pueden existir pedidos
        // (ahora cancelados) referenciando este turno. closeShiftInternal elimina
        // el turno cuando totalOrders == 0, lo cual viola shift_id NOT NULL en
        // orders. El auto-cierre siempre persiste el turno como CLOSED.
        WorkShiftSummary summary = calculateSummary(shift);
        if (summary.getTotalOrders() > 0) {
            workShiftSummaryRepository.save(summary);
        }

        shift.setClosedAt(OffsetDateTime.now());
        shift.setClosedBy(null);
        shift.setStatus(ShiftStatus.CLOSED);
        workShiftRepository.save(shift);
        branchRepository.updateAcceptingOrders(shift.getBranch().getId(), false);
    }

    public List<TopProductDTO> getTopProducts(UUID shiftId, Integer branchId) {
        WorkShift shift = workShiftRepository.findById(shiftId)
            .orElseThrow(() -> new BusinessException("Turno no encontrado"));
        if (!shift.getBranch().getId().equals(branchId)) {
            throw new BusinessException("El turno no pertenece a esta sucursal");
        }
        List<Object[]> rows = orderItemRepository.findTopProductsByShiftId(
            shiftId, OrderStatus.DELIVERED, PageRequest.of(0, 5));

        return rows.stream()
            .map(r -> new TopProductDTO((Integer) r[0], (String) r[1], (Long) r[2]))
            .toList();
    }
}
