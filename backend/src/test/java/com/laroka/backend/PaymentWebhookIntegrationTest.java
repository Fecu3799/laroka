package com.laroka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;

/**
 * US-TEST-04 — Test de integración con DB real (Postgres) del webhook de MercadoPago,
 * que además de activar el pedido dispara reembolsos automáticos en la carrera pago
 * aprobado / pedido ya cancelado.
 *
 * Postgres real vía @ActiveProfiles("test"). El gateway se reemplaza por @MockitoBean
 * (sin llamadas externas ni plata real). A diferencia del perfil test por defecto —donde
 * el secret vacío hace que la validación de firma se saltee— acá se configura un
 * mercadopago.webhook-secret real y skip=false para ejercitar la validación HMAC de
 * verdad: los payloads válidos se firman en el propio test.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "mercadopago.webhook-secret=" + PaymentWebhookIntegrationTest.WEBHOOK_SECRET,
        "debug.skip-webhook-signature-validation=false"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookIntegrationTest {

    static final String WEBHOOK_SECRET = "test-webhook-secret-1234567890";
    private static final int BRANCH_ID = 1;
    private static final String REQUEST_ID = "req-001";
    private static final String TS = "1700000000";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderStatusHistoryRepository historyRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PaymentGateway paymentGateway;

    @BeforeEach
    void seed() {
        Mockito.reset(paymentGateway);

        jdbcTemplate.execute(
            "TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");
        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
        jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
            "Test Branch", "Test Address", 1);
        jdbcTemplate.update("UPDATE branch SET accepting_orders = true WHERE id = ?", BRANCH_ID);
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Staff", "staff@test.com", "noop", "STAFF", 1);
        jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Test Category", 1);
        jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
            "Test Pizza", new java.math.BigDecimal("10.00"), 1, 1);
        jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, 1);
        jdbcTemplate.update(
            "INSERT INTO work_shift (id, branch_id, opened_by, opened_at, status) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'OPEN')",
            UUID.randomUUID(), BRANCH_ID, 1);
    }

    // ── Webhook aprobado válido → el pedido pasa a RECEIVED ───────────────────────

    @Test
    void approvedWebhook_transitionsOrderToReceived() throws Exception {
        UUID orderId = createMpOrder();
        initiatePayment(orderId);

        String paymentId = "mp-approved-001";
        sendApprovedWebhook(paymentId, orderId, validSignature(paymentId))
            .andExpect(status().isOk());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.RECEIVED);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getMercadopagoPaymentId()).isEqualTo(paymentId);
    }

    // ── Firma inválida → rechazado sin modificar el estado del pedido ─────────────

    @Test
    void invalidSignature_isRejected_withoutChangingOrderState() throws Exception {
        UUID orderId = createMpOrder();
        initiatePayment(orderId);

        String paymentId = "mp-badsig-001";
        // v1 arbitrario que no corresponde al HMAC real del payload → 422.
        sendApprovedWebhook(paymentId, orderId, "ts=" + TS + ",v1=deadbeefdeadbeef")
            .andExpect(status().isUnprocessableEntity());

        // El pedido sigue esperando el pago y el pago sigue PENDING: nada cambió.
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PENDING);
        // La firma se valida antes de consultar el pago en MP: nunca se llama al gateway.
        verify(paymentGateway, never()).fetchPayment(any());
    }

    // ── Webhook duplicado (mismo mercadopago_payment_id) → no re-actualiza ────────

    @Test
    void duplicateWebhook_doesNotDuplicateStatusUpdate() throws Exception {
        UUID orderId = createMpOrder();
        initiatePayment(orderId);

        String paymentId = "mp-dup-001";
        String signature = validSignature(paymentId);

        sendApprovedWebhook(paymentId, orderId, signature).andExpect(status().isOk());
        // Segundo webhook idéntico: el pago ya no está PENDING → se ignora.
        sendApprovedWebhook(paymentId, orderId, signature).andExpect(status().isOk());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.RECEIVED);
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.APPROVED);
        // El guard de duplicado corta antes de consultar el pago: fetchPayment una sola vez.
        verify(paymentGateway, times(1)).fetchPayment(paymentId);
        // Y una única transición PENDING_PAYMENT → RECEIVED en el historial.
        long receivedTransitions = historyRepository.findByOrderIdOrderByChangedAtAsc(orderId).stream()
            .filter(h -> h.getToStatus() == OrderStatus.RECEIVED)
            .count();
        assertThat(receivedTransitions).isEqualTo(1);
    }

    // ── Pago aprobado tras pedido ya cancelado → reembolso automático (carrera) ───

    @Test
    void approvedWebhookForCancelledOrder_triggersAutomaticRefund() throws Exception {
        UUID orderId = createMpOrder();
        initiatePayment(orderId);

        // Carrera: el pedido se cancela mientras el pago está en vuelo. El pago sigue
        // PENDING (una cancelación de pedido sin cobro no lo toca), así el webhook no
        // lo descarta por el guard de duplicado.
        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", orderId);

        String paymentId = "mp-cancelled-001";
        sendApprovedWebhook(paymentId, orderId, validSignature(paymentId))
            .andExpect(status().isOk());

        // Se disparó el reembolso automático y el pago quedó REFUNDED; el pedido sigue CANCELLED.
        verify(paymentGateway).refundPayment(paymentId);
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELLED);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private UUID createMpOrder() throws Exception {
        String body = """
            {
                "branchId": %d,
                "orderType": "TAKEAWAY",
                "paymentMethod": "MERCADOPAGO",
                "items": [{"productId": 1, "quantity": 1}]
            }
            """.formatted(BRANCH_ID);

        MvcResult result = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText());
    }

    private void initiatePayment(UUID orderId) throws Exception {
        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-" + orderId);
        mockMvc.perform(post("/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\"}"))
            .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions sendApprovedWebhook(
            String paymentId, UUID orderId, String signature) throws Exception {
        when(paymentGateway.fetchPayment(paymentId))
            .thenReturn(new PaymentGateway.PaymentInfo("approved", orderId.toString()));
        return mockMvc.perform(post("/payments/webhook")
                .param("data.id", paymentId)
                .header("x-signature", signature)
                .header("x-request-id", REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"type\":\"payment\",\"data\":{\"id\":\"%s\"}}", paymentId)));
    }

    /**
     * Firma HMAC-SHA256 válida con el mismo formato que valida PaymentService:
     * message = "id:{paymentId};request-id:{requestId};ts:{ts};", header = "ts=..,v1=..".
     */
    private String validSignature(String paymentId) throws Exception {
        String message = "id:" + paymentId + ";request-id:" + REQUEST_ID + ";ts:" + TS + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        return "ts=" + TS + ",v1=" + v1;
    }
}
