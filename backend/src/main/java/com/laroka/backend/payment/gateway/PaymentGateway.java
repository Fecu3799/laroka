package com.laroka.backend.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {

    String createPreference(UUID orderId, BigDecimal amount, BackUrls backUrls);

    PaymentInfo fetchPayment(String paymentId);

    String chargeQr(String mpPosId, UUID orderId, BigDecimal amount);

    void cancelQrCharge(String externalId);

    void refundPayment(String paymentId);

    record PaymentInfo(String status, String externalReference) {}

    record BackUrls(String success, String failure, String pending) {}
}
