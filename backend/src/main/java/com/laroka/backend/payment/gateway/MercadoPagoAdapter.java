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
    public String createPreference(UUID orderId, BigDecimal amount, PaymentGateway.BackUrls backUrls) {
        log.info("createPreference: orderId={}, amount={}, backUrls={}", orderId, amount, backUrls != null);

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

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", orderId.toString());
        body.put("notification_url", notificationUrl + "/payments/webhook");

        log.info("createPreference: backUrls detail — success={}, failure={}, pending={}", 
                    backUrls != null ? backUrls.success() : "NULL",
                    backUrls != null ? backUrls.failure() : "NULL", 
                    backUrls != null ? backUrls.pending() : "NULL"
                );

        if (backUrls != null) {
            body.put("back_urls", Map.of(
                    "success", backUrls.success(),
                    "failure", backUrls.failure(),
                    "pending", backUrls.pending()
            ));
        }

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

    @Override
    public String chargeQr(String mpPosId, UUID orderId, BigDecimal amount) {
        log.info("chargeQr: mpPosId={}, orderId={}, amount={}", mpPosId, orderId, amount);

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("chargeQr: accessToken not configured, returning sandbox external_id");
            return "SANDBOX_QR_" + orderId;
        }

        Map<String, Object> item = Map.of(
                "sku_number", "PEDIDO",
                "category", "food",
                "title", "Pedido LaRoka",
                "description", "Pedido LaRoka #" + orderId,
                "unit_measure", "unit",
                "quantity", 1,
                "unit_price", amount,
                "total_amount", amount
        );

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("external_reference", orderId.toString());
        body.put("title", "Pedido LaRoka");
        body.put("description", "Pedido LaRoka #" + orderId);
        body.put("total_amount", amount);
        body.put("items", List.of(item));

        String url = "https://api.mercadopago.com/instore/qrs/" + mpPosId + "/qrs";

        try {
            MpQrChargeResponse response = restClient.put()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(MpQrChargeResponse.class);

            if (response == null || response.externalId() == null) {
                throw new BusinessException("MercadoPago no devolvió un external_id para el cobro QR");
            }
            log.info("chargeQr: QR charged — externalId={}", response.externalId());
            return response.externalId();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("chargeQr: error calling MercadoPago QR API — mpPosId={}, error={}", mpPosId, e.getMessage());
            throw new BusinessException("Error al cargar el cobro en el QR: " + e.getMessage());
        }
    }

    @Override
    public void refundPayment(String paymentId) {
        log.info("refundPayment: paymentId={}", paymentId);

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("refundPayment: accessToken not configured, skipping refund — paymentId={}", paymentId);
            return;
        }

        String url = MP_PAYMENTS_URL + paymentId + "/refunds";
        try {
            restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
            log.info("refundPayment: refund accepted by MercadoPago — paymentId={}", paymentId);
        } catch (Exception e) {
            log.error("refundPayment: error calling MercadoPago refund API — paymentId={}, error={}", paymentId, e.getMessage());
            throw new BusinessException("Error al solicitar el reembolso en MercadoPago: " + e.getMessage());
        }
    }

    @Override
    public void cancelQrCharge(String externalId) {
        log.info("cancelQrCharge: externalId={}", externalId);

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("cancelQrCharge: accessToken not configured, skipping cancel");
            return;
        }

        String url = "https://api.mercadopago.com/instore/qrs/" + externalId;

        try {
            restClient.delete()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("cancelQrCharge: QR charge cancelled — externalId={}", externalId);
        } catch (Exception e) {
            log.error("cancelQrCharge: error cancelling QR charge — externalId={}, error={}", externalId, e.getMessage());
            throw new BusinessException("Error al cancelar el cobro QR activo: " + e.getMessage());
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

    private record MpQrChargeResponse(
            @JsonProperty("external_id") String externalId
    ) {}
}
