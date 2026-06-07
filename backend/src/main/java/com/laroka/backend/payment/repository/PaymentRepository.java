package com.laroka.backend.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByMercadopagoPaymentId(String mercadopagoPaymentId);
    boolean existsByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);
    Optional<Payment> findByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);
    List<Payment> findByOrderIdIn(Collection<UUID> orderIds);
}
