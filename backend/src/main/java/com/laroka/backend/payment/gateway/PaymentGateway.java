package com.laroka.backend.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {

    String createPreference(UUID orderId, BigDecimal amount, BackUrls backUrls);

    PaymentInfo fetchPayment(String paymentId);

    String chargeQr(String mpPosId, UUID orderId, BigDecimal amount);

    void cancelQrCharge(String externalId);

    /**
     * Reembolsa un pago. Si {@code amount} es null, se reembolsa el total
     * (comportamiento histórico). Si tiene valor, se reembolsa ese monto parcial.
     */
    void refundPayment(String paymentId, BigDecimal amount);

    /** Reembolso total: atajo de {@link #refundPayment(String, BigDecimal)} con amount null. */
    default void refundPayment(String paymentId) {
        refundPayment(paymentId, null);
    }

    record PaymentInfo(String status, String externalReference) {}

    record BackUrls(String success, String failure, String pending) {}
}
