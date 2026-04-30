package com.laroka.backend.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
    /**
     * Creates a checkout preference in MercadoPago.
     * @return the init_point URL to redirect the customer to
     */
    String createPreference(UUID orderId, BigDecimal amount);
}
