package com.laroka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

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
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-TEST-06 — Test de integración con DB real (Postgres) del flujo completo de
 * reembolsos, mockeando {@code PaymentGateway.refundPayment} (sin plata real ni
 * llamadas externas).
 *
 * Cubre: reembolso total al cancelar un pago MP antes de preparación; reembolso
 * parcial del 85% del subtotal al aprobar una cancelación tardía; reembolso fallido
 * que no bloquea la cancelación y deja el pago en REFUND_FAILED con el monto pendiente;
 * el endpoint ADMIN de reintento (éxito, sin fallo pendiente → 422, y 403 para
 * MANAGER/STAFF).
 *
 * Nota de diseño: se mantiene independiente de ShiftReceptionFlowIntegrationTest
 * (US-TEST-01) pese a compartir la idea de "turno + pedido + pago". Los seeds y los
 * roles divergen (acá se necesita un pago MP ya aprobado y tokens ADMIN/MANAGER/STAFF
 * para el retry-refund), y una clase base común acoplaría dos escenarios grandes por
 * un ahorro chico de boilerplate estable. Con solo dos consumidores no aplica la regla
 * de tres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundFlowIntegrationTest {

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int TENANT_ID = 1;
    private static final int BRANCH_ID = 1;
    private static final int STAFF_USER_ID = 1;
    private static final int ADMIN_USER_ID = 2;
    private static final int MANAGER_USER_ID = 3;
    private static final String MP_PAYMENT_ID = "mp-refund-001";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PaymentGateway paymentGateway;

    @BeforeEach
    void seed() {
        Mockito.reset(paymentGateway);

        jdbcTemplate.execute(
            "TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");
        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
        jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
            "Test Branch", "Test Address", TENANT_ID);
        jdbcTemplate.update("UPDATE branch SET accepting_orders = true WHERE id = ?", BRANCH_ID);
        // STAFF (id=1) opera pedidos; ADMIN (id=2) reintenta reembolsos; MANAGER (id=3)
        // para el caso de autorización (403).
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Staff", "staff@test.com", "noop", "STAFF", BRANCH_ID);
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Admin", "admin@test.com", "noop", "ADMIN", BRANCH_ID);
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Manager", "manager@test.com", "noop", "MANAGER", BRANCH_ID);
        jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Test Category", 1);
        jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
            "Test Pizza", new BigDecimal("10.00"), 1, 1);
        jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, 1);
        jdbcTemplate.update(
            "INSERT INTO work_shift (id, branch_id, opened_by, opened_at, status) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'OPEN')",
            UUID.randomUUID(), BRANCH_ID, STAFF_USER_ID);
    }

    // ── Cancelar pago MP antes de preparación → reembolso TOTAL ───────────────────

    @Test
    void cancelBeforePreparation_triggersTotalRefund() throws Exception {
        UUID orderId = createApprovedMpOrder();
        BigDecimal totalAmount = orderRepository.findById(orderId).orElseThrow().getTotalAmount();

        // Cancelación directa desde RECEIVED (client). refundPayment (void) es no-op → éxito.
        cancelOrder(orderId, null);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELLED);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(totalAmount);
        // Reembolso TOTAL: amount null.
        verify(paymentGateway).refundPayment(MP_PAYMENT_ID, null);
    }

    // ── Aprobar CANCELLATION_REQUESTED en preparación → reembolso PARCIAL 85% ──────

    @Test
    void approveCancellationRequestInPreparation_triggersPartialRefund() throws Exception {
        UUID orderId = createApprovedMpOrder();
        BigDecimal subtotal = orderRepository.findById(orderId).orElseThrow().getSubtotal();
        BigDecimal expectedPartial = subtotal.multiply(new BigDecimal("0.85")).setScale(2, java.math.RoundingMode.HALF_UP);

        // RECEIVED → IN_PREPARATION (backoffice), luego el client pide cancelar (tardía)
        // → CANCELLATION_REQUESTED, y el backoffice la aprueba.
        transition(orderId, "IN_PREPARATION");
        cancelOrder(orderId, "Ya no lo quiero");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELLATION_REQUESTED);

        resolveCancellation(orderId, "APPROVE");

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELLED);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(expectedPartial);
        // Reembolso PARCIAL: 85% del subtotal.
        verify(paymentGateway).refundPayment(eq(MP_PAYMENT_ID),
            argThat(amount -> amount != null && amount.compareTo(expectedPartial) == 0));
    }

    // ── Reembolso fallido no bloquea la cancelación → REFUND_FAILED con monto ─────

    @Test
    void refundFailure_doesNotBlockCancellation_marksRefundFailed() throws Exception {
        UUID orderId = createApprovedMpOrder();
        BigDecimal totalAmount = orderRepository.findById(orderId).orElseThrow().getTotalAmount();

        doThrow(new RuntimeException("gateway down")).when(paymentGateway).refundPayment(any(), any());

        // La cancelación NO se bloquea pese al fallo del gateway (204).
        cancelOrder(orderId, null);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELLED);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        // El monto pendiente queda persistido para el reintento manual.
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(totalAmount);
    }

    // ── retry-refund como ADMIN → REFUNDED en éxito ───────────────────────────────

    @Test
    void retryRefundAsAdmin_updatesToRefundedOnSuccess() throws Exception {
        UUID orderId = createApprovedMpOrder();
        BigDecimal totalAmount = orderRepository.findById(orderId).orElseThrow().getTotalAmount();

        // Primer refund (durante la cancelación) falla; el segundo (retry) tiene éxito.
        doThrow(new RuntimeException("gateway down")).doNothing()
            .when(paymentGateway).refundPayment(any(), any());
        cancelOrder(orderId, null);
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.REFUND_FAILED);

        mockMvc.perform(post("/backoffice/orders/" + orderId + "/retry-refund")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Branch-Id", String.valueOf(BRANCH_ID)))
            .andExpect(status().isNoContent());

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // El reintento usa el mismo monto persistido.
        verify(paymentGateway).refundPayment(eq(MP_PAYMENT_ID),
            argThat(amount -> amount != null && amount.compareTo(totalAmount) == 0));
    }

    // ── retry-refund sin reembolso fallido pendiente → 422 ────────────────────────

    @Test
    void retryRefundWithoutFailedRefund_returns422() throws Exception {
        // Pago APPROVED (no REFUND_FAILED): no hay nada que reintentar.
        UUID orderId = createApprovedMpOrder();

        mockMvc.perform(post("/backoffice/orders/" + orderId + "/retry-refund")
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Branch-Id", String.valueOf(BRANCH_ID)))
            .andExpect(status().isUnprocessableEntity());
    }

    // ── retry-refund para MANAGER / STAFF → 403 ───────────────────────────────────

    @Test
    void retryRefundAsManagerOrStaff_returns403() throws Exception {
        UUID orderId = createApprovedMpOrder();

        mockMvc.perform(post("/backoffice/orders/" + orderId + "/retry-refund")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/backoffice/orders/" + orderId + "/retry-refund")
                .header("Authorization", "Bearer " + staffToken()))
            .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    /** Crea un pedido TAKEAWAY MERCADOPAGO y lo lleva a RECEIVED con pago APPROVED. */
    private UUID createApprovedMpOrder() throws Exception {
        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-001");

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
        UUID orderId = UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText());

        mockMvc.perform(post("/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\"}"))
            .andExpect(status().isOk());

        // Webhook aprobado (firma no validada: el perfil test no configura secret).
        when(paymentGateway.fetchPayment(MP_PAYMENT_ID))
            .thenReturn(new PaymentGateway.PaymentInfo("approved", orderId.toString()));
        mockMvc.perform(post("/payments/webhook")
                .param("data.id", MP_PAYMENT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"type\":\"payment\",\"data\":{\"id\":\"%s\"}}", MP_PAYMENT_ID)))
            .andExpect(status().isOk());

        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.APPROVED);
        return orderId;
    }

    private void cancelOrder(UUID orderId, String reason) throws Exception {
        var request = post("/orders/" + orderId + "/cancel")
            .contentType(MediaType.APPLICATION_JSON);
        request = reason != null
            ? request.content("{\"reason\":\"" + reason + "\"}")
            : request.content("{}");
        mockMvc.perform(request).andExpect(status().isNoContent());
    }

    private void transition(UUID orderId, String nextStatus) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + staffToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextStatus\":\"" + nextStatus + "\"}"))
            .andExpect(status().isNoContent());
    }

    private void resolveCancellation(UUID orderId, String action) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/cancel-request")
                .header("Authorization", "Bearer " + staffToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\"}"))
            .andExpect(status().isNoContent());
    }

    private String staffToken() {
        return tokenFor(STAFF_USER_ID, "STAFF", BRANCH_ID, null);
    }

    private String managerToken() {
        return tokenFor(MANAGER_USER_ID, "MANAGER", BRANCH_ID, null);
    }

    private String adminToken() {
        return tokenFor(ADMIN_USER_ID, "ADMIN", null, TENANT_ID);
    }

    private String tokenFor(int userId, String role, Integer branchId, Integer tenantId) {
        var builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        if (branchId != null) {
            builder.claim("branchId", branchId);
        }
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        return builder.compact();
    }
}
