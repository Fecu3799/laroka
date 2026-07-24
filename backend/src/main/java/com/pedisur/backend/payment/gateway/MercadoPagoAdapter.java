package com.pedisur.backend.payment.gateway;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedisur.backend.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MercadoPagoAdapter implements PaymentGateway {

    private static final String MP_PREFERENCES_URL = "https://api.mercadopago.com/checkout/preferences";
    private static final String MP_PAYMENTS_URL = "https://api.mercadopago.com/v1/payments/";

    // Timeouts de red conservadores para TODAS las llamadas a MercadoPago. Sin esto el
    // RestClient hereda timeout infinito: una conexión colgada retiene el thread (y, en
    // los flujos donde la llamada MP ocurre dentro de una @Transactional, la conexión
    // Hikari) indefinidamente. connect = handshake TCP/TLS; read = espera de respuesta.
    private static final Duration MP_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MP_READ_TIMEOUT = Duration.ofSeconds(15);

    // Solo para logging de diagnóstico: parsea el cuerpo crudo de MP a un record
    // enriquecido sin romper si aparecen campos nuevos. No participa de la lógica
    // de negocio (el valor de retorno sigue saliendo de status + external_reference).
    private static final ObjectMapper DEBUG_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String accessToken;
    private final String notificationUrl;
    private final RestClient restClient;

    @Autowired
    public MercadoPagoAdapter(
        @Value("${mercadopago.key:}") String accessToken,
        @Value("${mercadopago.notifications-url:}") String notificationUrl
        ) {
        this(accessToken, notificationUrl, RestClient.builder().requestFactory(timeoutRequestFactory()));
    }

    // Factory con timeouts explícitos. Solo se aplica en el path de producción; el
    // constructor de test recibe un builder ligado a MockRestServiceServer y no pasa
    // por acá, así que los tests siguen sin salir a la red.
    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(MP_CONNECT_TIMEOUT);
        factory.setReadTimeout(MP_READ_TIMEOUT);
        return factory;
    }

    // Constructor de test: permite inyectar un RestClient.Builder ligado a un
    // MockRestServiceServer para verificar el body de las requests sin HTTP real.
    MercadoPagoAdapter(String accessToken, String notificationUrl, RestClient.Builder restClientBuilder) {
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
        this.restClient = restClientBuilder.build();
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
            // auto_return="approved": MP redirige automáticamente a back_urls.success
            // (sin que el usuario toque el botón de volver) solo cuando el pago fue
            // aprobado. MP exige un back_urls.success válido para aceptar auto_return,
            // por eso va dentro de este guard. Los únicos valores válidos son
            // "approved" y "all"; ninguno redirige automáticamente en pending/rejected.
            body.put("auto_return", "approved");
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
            // Se recibe el cuerpo crudo (String) para poder loguear la respuesta
            // COMPLETA de MercadoPago a nivel DEBUG — incluyendo status_detail y
            // cualquier campo que el record tipado no mapee — sin alterar el valor
            // de retorno (que sigue derivándose de status + external_reference).
            String rawBody = restClient.get()
                    .uri(MP_PAYMENTS_URL + paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            // Volcado íntegro primero: si el parseo fallara, el crudo queda visible igual.
            log.debug("fetchPayment: raw MP response — paymentId={}, body={}", paymentId, rawBody);

            if (rawBody == null) {
                throw new BusinessException("MercadoPago no devolvió datos del pago");
            }

            MpPaymentResponse response = DEBUG_MAPPER.readValue(rawBody, MpPaymentResponse.class);
            if (response == null) {
                throw new BusinessException("MercadoPago no devolvió datos del pago");
            }

            // Línea estructurada de alta señal: status_detail es el motivo puntual del
            // rechazo (más granular que status, ej. "cc_rejected_insufficient_amount"),
            // junto al resto de campos diagnósticos que hoy no se mapeaban.
            log.debug("fetchPayment: parsed MP payment — id={}, status={}, statusDetail={}, "
                            + "paymentMethodId={}, paymentTypeId={}, transactionAmount={}, currencyId={}, "
                            + "dateApproved={}, dateCreated={}, externalReference={}",
                    response.id(), response.status(), response.statusDetail(),
                    response.paymentMethodId(), response.paymentTypeId(), response.transactionAmount(),
                    response.currencyId(), response.dateApproved(), response.dateCreated(),
                    response.externalReference());

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
    public void refundPayment(String paymentId, BigDecimal amount) {
        boolean partial = amount != null;
        log.info("refundPayment: paymentId={}, partial={}, amount={}", paymentId, partial, amount);

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("refundPayment: accessToken not configured, skipping refund — paymentId={}", paymentId);
            return;
        }

        // amount null → body vacío = reembolso total. amount presente → body con el
        // monto = reembolso parcial. (POST /v1/payments/{id}/refunds)
        Map<String, Object> body = partial ? Map.of("amount", amount) : Map.of();

        // MercadoPago EXIGE X-Idempotency-Key en reembolsos parciales (y lo acepta en
        // los totales), sin él responde 400 "Header X-Idempotency-Key can't be null".
        // Generamos una key nueva por cada intento de llamada (no determinística por
        // paymentId+amount): la idempotencia real de negocio la garantiza el estado del
        // Payment (REFUNDED / REFUND_FAILED) en OrderService, no esta key. Una key
        // determinística haría que MP devolviera cacheado el resultado de un intento
        // previo fallido, impidiendo el reintento manual (US-17-05).
        String idempotencyKey = UUID.randomUUID().toString();

        String url = MP_PAYMENTS_URL + paymentId + "/refunds";
        try {
            restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("refundPayment: refund accepted by MercadoPago — paymentId={}, partial={}, idempotencyKey={}",
                    paymentId, partial, idempotencyKey);
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

    // Enriquecido solo para diagnóstico: status_detail y demás campos se usan en el
    // logging DEBUG de fetchPayment. La lógica de negocio sigue usando únicamente
    // status y externalReference. ignoreUnknown = true para no romper ante campos
    // nuevos del payload de MP (que trae muchos más de los declarados acá).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MpPaymentResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("status") String status,
            @JsonProperty("status_detail") String statusDetail,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("payment_method_id") String paymentMethodId,
            @JsonProperty("payment_type_id") String paymentTypeId,
            @JsonProperty("transaction_amount") BigDecimal transactionAmount,
            @JsonProperty("currency_id") String currencyId,
            @JsonProperty("date_approved") String dateApproved,
            @JsonProperty("date_created") String dateCreated
    ) {}

    private record MpQrChargeResponse(
            @JsonProperty("external_id") String externalId
    ) {}
}
