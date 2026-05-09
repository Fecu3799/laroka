package com.laroka.backend.payment.gateway;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.laroka.backend.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MercadoPagoAdapter implements PaymentGateway {

    private static final String MP_PREFERENCES_URL = "https://api.mercadopago.com/checkout/preferences";
    private static final String MP_PAYMENTS_URL = "https://api.mercadopago.com/v1/payments/";

    private final String accessToken;
    private final String notificationUrl;
    private final RestClient restClient;

    public MercadoPagoAdapter(
        @Value("${mercadopago.key:}") String accessToken, 
        @Value("${mercadopago.notifications-url:}") String notificationUrl
        ) {
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String createPreference(UUID orderId, BigDecimal amount) {
        log.info("createPreference: orderId={}, amount={}", orderId, amount);

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("createPreference: accessToken not configured, returning sandbox URL");
            return "https://mp-sandbox.mercadopago.com/checkout?order=" + orderId;
        }

        Map<String, Object> item = Map.of(
                "title", "Pedido LaRoka",
                "quantity", 1,
                "unit_price", amount,
                "currency_id", "ARS"
        );

        Map<String, Object> body = Map.of(
                "items", List.of(item),
                "external_reference", orderId.toString(),
                "notification_url", notificationUrl + "/payments/webhook"
        );

        try {
            MpPreferenceResponse response = restClient.post()
                    .uri(MP_PREFERENCES_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(MpPreferenceResponse.class);

            if (response == null || response.initPoint() == null) {
                throw new BusinessException("MercadoPago no devolvió un link de pago válido");
            }
            log.info("createPreference: MP response received — initPoint={}", response.initPoint());
            return response.initPoint();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("createPreference: error calling MercadoPago — orderId={}, error={}", orderId, e.getMessage());
            throw new BusinessException("Error al crear la preferencia de pago: " + e.getMessage());
        }
    }

    @Override
    public PaymentInfo fetchPayment(String paymentId) {
        log.info("fetchPayment: paymentId={}", paymentId);

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("fetchPayment: accessToken not configured, returning sandbox approved response");
            return new PaymentInfo("approved", paymentId);
        }

        try {
            MpPaymentResponse response = restClient.get()
                    .uri(MP_PAYMENTS_URL + paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MpPaymentResponse.class);

            if (response == null) {
                throw new BusinessException("MercadoPago no devolvió datos del pago");
            }
            log.info("fetchPayment: MP response received — status={}, externalReference={}", response.status(), response.externalReference());
            return new PaymentInfo(response.status(), response.externalReference());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("fetchPayment: error calling MercadoPago — paymentId={}, error={}", paymentId, e.getMessage());
            throw new BusinessException("Error al consultar el pago en MercadoPago: " + e.getMessage());
        }
    }

    private record MpPreferenceResponse(
            @JsonProperty("id") String id,
            @JsonProperty("init_point") String initPoint
    ) {}

    private record MpPaymentResponse(
            @JsonProperty("status") String status,
            @JsonProperty("external_reference") String externalReference
    ) {}
}
