package com.laroka.backend.payment.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);

    // SELECT ... FOR UPDATE sobre la fila del pago. Usado por retryRefund para
    // serializar reintentos concurrentes (doble click del operador): la segunda
    // transacción bloquea hasta que la primera commitea, y al re-leer el estado ve
    // REFUNDED y el guard la rechaza — evitando dos refunds a MercadoPago.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(UUID orderId);

    // Reembolsos fallidos sin resolver hace más de :cutoff y que aún no recibieron
    // el aviso de demora (US-17-08). join fetch de order + branch para armar el
    // mensaje (contacto de soporte) sin lazy loading fuera de sesión.
    @Query("""
            select p from Payment p
            join fetch p.order o
            join fetch o.branch
            where p.status = :status
              and p.refundFailedAt < :cutoff
              and p.refundDelayNotified = false
            """)
    List<Payment> findStaleUnnotifiedRefundFailures(PaymentStatus status, LocalDateTime cutoff);
    Optional<Payment> findByMercadopagoPaymentId(String mercadopagoPaymentId);
    boolean existsByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);
    Optional<Payment> findByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);
    List<Payment> findByOrderIdIn(Collection<UUID> orderIds);
}
