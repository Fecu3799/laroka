package com.laroka.backend.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {

    String createPreference(UUID orderId, BigDecimal amount, BackUrls backUrls);

    PaymentInfo fetchPayment(String paymentId);

    record PaymentInfo(String status, String externalReference) {}

    record BackUrls(String success, String failure, String pending) {}
}
